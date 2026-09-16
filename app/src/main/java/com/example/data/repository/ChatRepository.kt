package com.example.data.repository

import com.example.data.api.ChatMessageDto
import com.example.data.api.HcnsecApiClient
import com.example.data.api.StreamEvent
import com.example.data.local.dao.ConversationDao
import com.example.data.local.dao.MessageDao
import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.MessageEntity
import com.example.data.workspace.ZipWorkspaceManager
import com.example.domain.model.AiModel
import com.example.domain.model.AttachmentItem
import com.example.domain.model.ChatMessage
import com.example.domain.model.TraceStep
import com.example.domain.model.TraceStepStatus
import com.example.domain.router.AutoModelRouter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class ChatRepository(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val apiClient: HcnsecApiClient,
    private val modelRouter: AutoModelRouter,
    private val zipWorkspaceManager: ZipWorkspaceManager
) {
    private val _currentTrace = MutableStateFlow<List<TraceStep>>(emptyList())
    val currentTrace = _currentTrace.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating = _isGenerating.asStateFlow()

    private var activeGenerationJob: Job? = null

    fun getConversations(workspaceId: String? = null): Flow<List<ConversationEntity>> {
        return if (workspaceId != null) {
            conversationDao.getWorkspaceConversations(workspaceId)
        } else {
            conversationDao.getAllConversations()
        }
    }

    fun getMessages(conversationId: String): Flow<List<ChatMessage>> {
        return messageDao.getMessagesForConversation(conversationId).map { entities ->
            entities.map { entity ->
                ChatMessage(
                    id = entity.id,
                    conversationId = entity.conversationId,
                    role = entity.role,
                    content = entity.content,
                    reasoningContent = entity.reasoningContent,
                    timestamp = entity.timestamp,
                    tokenCount = entity.tokenCount,
                    isStreaming = false
                )
            }
        }
    }

    suspend fun getConversation(id: String): ConversationEntity? = withContext(Dispatchers.IO) {
        conversationDao.getConversationById(id)
    }

    suspend fun saveDraftPrompt(id: String, draft: String?) = withContext(Dispatchers.IO) {
        conversationDao.updateDraftPrompt(id, draft)
    }

    suspend fun createConversation(
        title: String = "New Conversation",
        workspaceId: String? = null,
        model: String = "auto"
    ): ConversationEntity = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val entity = ConversationEntity(
            id = id,
            title = title,
            workspaceId = workspaceId,
            selectedModel = model
        )
        conversationDao.insert(entity)
        entity
    }

    suspend fun renameConversation(id: String, newTitle: String) = withContext(Dispatchers.IO) {
        conversationDao.rename(id, newTitle)
    }

    suspend fun togglePinConversation(id: String, currentPin: Boolean) = withContext(Dispatchers.IO) {
        conversationDao.setPinned(id, !currentPin)
    }

    suspend fun deleteConversation(id: String) = withContext(Dispatchers.IO) {
        messageDao.deleteByConversationId(id)
        conversationDao.deleteById(id)
    }

    /**
     * Sends a user message and streams assistant response from HCNSEC API.
     */
    suspend fun sendMessage(
        conversationId: String,
        userPrompt: String,
        selectedModelId: String,
        availableModels: List<AiModel>,
        attachments: List<AttachmentItem> = emptyList(),
        workspaceId: String? = null,
        onChunkReceived: (String, String?) -> Unit, // contentChunk, reasoningChunk
        onError: (String) -> Unit,
        onCompleted: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (userPrompt.isBlank() && attachments.isEmpty()) return@withContext

        _isGenerating.value = true
        val steps = mutableListOf<TraceStep>()

        // Step 1: Request understanding
        steps.add(TraceStep("step_understand", "Understand user request", "Analyzing prompt and intent", TraceStepStatus.RUNNING))
        _currentTrace.value = steps.toList()

        // Persist User Message
        val userMsgId = UUID.randomUUID().toString()
        val userEntity = MessageEntity(
            id = userMsgId,
            conversationId = conversationId,
            role = "user",
            content = userPrompt,
            timestamp = System.currentTimeMillis()
        )
        messageDao.insert(userEntity)

        // Step 1 completed
        updateTraceStep(steps, "step_understand", TraceStepStatus.COMPLETED)

        // Step 2: Context and File inspection
        steps.add(TraceStep("step_context", "Inspect context & workspace", "Scanning attached files and project tree", TraceStepStatus.RUNNING))
        _currentTrace.value = steps.toList()

        val additionalContext = StringBuilder()

        // Workspace intelligence context injection
        if (workspaceId != null) {
            val relevantChunks = zipWorkspaceManager.getRelevantWorkspaceChunks(workspaceId, userPrompt, maxTotalChunks = 4)
            if (relevantChunks.isNotEmpty()) {
                additionalContext.append("\n\n--- WORKSPACE CODE CONTEXT ---\n")
                for (chunk in relevantChunks) {
                    additionalContext.append("File: ${chunk.filePath} [part ${chunk.chunkIndex}/${chunk.totalChunks}]:\n")
                    additionalContext.append("```\n${chunk.content}\n```\n\n")
                }
            }
        }

        // Attached text/document files
        for (att in attachments) {
            if (!att.extractedText.isNullOrBlank()) {
                additionalContext.append("\n\n--- ATTACHED FILE: ${att.name} ---\n")
                additionalContext.append(att.extractedText.take(10_000))
                additionalContext.append("\n")
            }
        }

        updateTraceStep(steps, "step_context", TraceStepStatus.COMPLETED)

        // Step 3: Model Routing
        steps.add(TraceStep("step_route", "Select HCNSEC model", "Evaluating capabilities and task profile", TraceStepStatus.RUNNING))
        _currentTrace.value = steps.toList()

        val resolvedModelId: String
        if (selectedModelId == "auto") {
            val task = modelRouter.classifyTask(
                query = userPrompt,
                hasAttachments = attachments.isNotEmpty(),
                hasZipWorkspace = workspaceId != null,
                hasImages = attachments.any { it.mimeType.startsWith("image/") }
            )
            val routed = modelRouter.selectModel(task, availableModels)
            resolvedModelId = routed?.id ?: availableModels.firstOrNull()?.id ?: "deepseek-chat"
            updateTraceStep(steps, "step_route", TraceStepStatus.COMPLETED, "Selected: $resolvedModelId (${task.label})")
        } else {
            resolvedModelId = selectedModelId
            updateTraceStep(steps, "step_route", TraceStepStatus.COMPLETED, "Using: $resolvedModelId")
        }

        // Step 4: Stream response from HCNSEC API
        steps.add(TraceStep("step_stream", "Generate response", "Streaming tokens via HCNSEC API", TraceStepStatus.RUNNING))
        _currentTrace.value = steps.toList()

        // Fetch conversation history
        val history = messageDao.getMessagesSnapshot(conversationId).takeLast(12)
        val apiMessages = mutableListOf<ChatMessageDto>()

        // System prompt
        apiMessages.add(
            ChatMessageDto(
                role = "system",
                content = "You are SALi-HCNSEC, a production-quality personal AI Agent powered exclusively by HCNSEC. Provide accurate, clean, structured responses with clear markdown and syntax-highlighted code blocks."
            )
        )

        for (msg in history) {
            if (msg.id == userMsgId && additionalContext.isNotEmpty()) {
                apiMessages.add(ChatMessageDto(role = msg.role, content = msg.content + additionalContext.toString()))
            } else {
                apiMessages.add(ChatMessageDto(role = msg.role, content = msg.content))
            }
        }

        val assistantMsgId = UUID.randomUUID().toString()
        var fullAssistantContent = ""
        var fullReasoningContent: String? = null

        var isInsideThinkTag = false
        var lastPeriodicSaveTime = System.currentTimeMillis()

        suspend fun periodicAutoSave() {
            val now = System.currentTimeMillis()
            if (now - lastPeriodicSaveTime >= 1000L && (fullAssistantContent.isNotEmpty() || !fullReasoningContent.isNullOrEmpty())) {
                lastPeriodicSaveTime = now
                messageDao.insert(
                    MessageEntity(
                        id = assistantMsgId,
                        conversationId = conversationId,
                        role = "assistant",
                        content = fullAssistantContent,
                        reasoningContent = fullReasoningContent,
                        timestamp = now
                    )
                )
            }
        }

        try {
            apiClient.streamChatCompletion(
                model = resolvedModelId,
                messages = apiMessages
            ).collect { event ->
                when (event) {
                    is StreamEvent.Content -> {
                        var text = event.text
                        if (!isInsideThinkTag && text.contains("<think>")) {
                            val thinkIndex = text.indexOf("<think>")
                            val beforeThink = text.substring(0, thinkIndex)
                            if (beforeThink.isNotEmpty()) {
                                fullAssistantContent += beforeThink
                                onChunkReceived(beforeThink, null)
                            }
                            isInsideThinkTag = true
                            text = text.substring(thinkIndex + 7)
                            updateTraceStep(steps, "step_stream", TraceStepStatus.RUNNING, "Thinking & analyzing step-by-step...")
                        }

                        if (isInsideThinkTag) {
                            if (text.contains("</think>")) {
                                val closeIndex = text.indexOf("</think>")
                                val thinkPart = text.substring(0, closeIndex)
                                val afterThink = text.substring(closeIndex + 8)
                                if (thinkPart.isNotEmpty()) {
                                    fullReasoningContent = (fullReasoningContent ?: "") + thinkPart
                                    onChunkReceived("", thinkPart)
                                }
                                isInsideThinkTag = false
                                updateTraceStep(steps, "step_stream", TraceStepStatus.RUNNING, "Formulating final response...")
                                if (afterThink.isNotEmpty()) {
                                    fullAssistantContent += afterThink
                                    onChunkReceived(afterThink, null)
                                }
                            } else {
                                if (text.isNotEmpty()) {
                                    fullReasoningContent = (fullReasoningContent ?: "") + text
                                    onChunkReceived("", text)
                                }
                            }
                        } else {
                            if (fullReasoningContent != null && fullAssistantContent.isEmpty()) {
                                updateTraceStep(steps, "step_stream", TraceStepStatus.RUNNING, "Streaming formatted answer...")
                            }
                            fullAssistantContent += text
                            onChunkReceived(text, null)
                        }
                        periodicAutoSave()
                    }
                    is StreamEvent.Reasoning -> {
                        updateTraceStep(steps, "step_stream", TraceStepStatus.RUNNING, "Deep reasoning & analysis (DeepSeek-R1)...")
                        fullReasoningContent = (fullReasoningContent ?: "") + event.reasoningText
                        onChunkReceived("", event.reasoningText)
                        periodicAutoSave()
                    }
                    is StreamEvent.Completed -> {
                        updateTraceStep(steps, "step_stream", TraceStepStatus.COMPLETED, "Completed successfully")
                    }
                    is StreamEvent.Error -> {
                        updateTraceStep(steps, "step_stream", TraceStepStatus.FAILED, event.message)
                        onError(event.message)
                    }
                }
            }

            // Save assistant message to Room
            if (fullAssistantContent.isNotEmpty() || !fullReasoningContent.isNullOrEmpty()) {
                val assistantEntity = MessageEntity(
                    id = assistantMsgId,
                    conversationId = conversationId,
                    role = "assistant",
                    content = fullAssistantContent,
                    reasoningContent = fullReasoningContent,
                    timestamp = System.currentTimeMillis()
                )
                messageDao.insert(assistantEntity)

                // Auto rename conversation if it's new
                if (history.size <= 2) {
                    val autoTitle = userPrompt.take(30).trim()
                    if (autoTitle.isNotEmpty()) {
                        conversationDao.rename(conversationId, autoTitle)
                    }
                }
            }

            onCompleted(fullAssistantContent)
        } catch (e: CancellationException) {
            updateTraceStep(steps, "step_stream", TraceStepStatus.FAILED, "Generation stopped by user")
            if (fullAssistantContent.isNotEmpty()) {
                messageDao.insert(
                    MessageEntity(
                        id = assistantMsgId,
                        conversationId = conversationId,
                        role = "assistant",
                        content = fullAssistantContent + "\n\n*(Generation stopped)*",
                        reasoningContent = fullReasoningContent,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
            throw e
        } catch (e: Exception) {
            updateTraceStep(steps, "step_stream", TraceStepStatus.FAILED, e.localizedMessage ?: "Generation failed")
            onError(e.localizedMessage ?: "Unexpected error")
        } finally {
            _isGenerating.value = false
        }
    }

    private fun updateTraceStep(
        steps: MutableList<TraceStep>,
        stepId: String,
        status: TraceStepStatus,
        detail: String? = null
    ) {
        val index = steps.indexOfFirst { it.id == stepId }
        if (index != -1) {
            val current = steps[index]
            steps[index] = current.copy(
                status = status,
                detail = detail ?: current.detail
            )
            _currentTrace.value = steps.toList()
        }
    }

    fun stopGeneration() {
        activeGenerationJob?.cancel()
        _isGenerating.value = false
    }

    fun clearTrace() {
        _currentTrace.value = emptyList()
    }
}
