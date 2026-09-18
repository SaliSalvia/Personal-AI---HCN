package com.example.ui.screens.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.WorkspaceEntity
import com.example.data.repository.ChatRepository
import com.example.data.repository.ModelRepository
import com.example.data.repository.WorkspaceRepository
import com.example.data.repository.SourceDocumentRepository
import com.example.data.security.ApiKeyRepository
import com.example.domain.model.AiModel
import com.example.domain.model.AttachmentItem
import com.example.domain.model.AttachmentStatus
import com.example.domain.model.ChatMessage
import com.example.domain.model.TraceStep
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val modelRepository: ModelRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val sourceDocumentRepository: SourceDocumentRepository,
    private val apiKeyRepository: ApiKeyRepository
) : ViewModel() {

    companion object {
        /** How often streamed tokens are pushed to the UI (about 25 fps). */
        private const val STREAM_FLUSH_INTERVAL_MS = 40L

        /** Hard cap for a single non-ZIP attachment read into memory. */
        private const val MAX_TEXT_ATTACHMENT_BYTES = 2L * 1024 * 1024
    }

    private val _currentConversationId = MutableStateFlow<String?>(null)
    val currentConversationId = _currentConversationId.asStateFlow()

    private val _activeWorkspaceId = MutableStateFlow<String?>(null)
    val activeWorkspaceId = _activeWorkspaceId.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText = _inputText.asStateFlow()

    private val _selectedModelId = MutableStateFlow(apiKeyRepository.getDefaultModel())
    val selectedModelId = _selectedModelId.asStateFlow()

    private val _attachments = MutableStateFlow<List<AttachmentItem>>(emptyList())
    val attachments = _attachments.asStateFlow()

    private val _streamingMessage = MutableStateFlow<ChatMessage?>(null)
    val streamingMessage = _streamingMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    val isGenerating = chatRepository.isGenerating
    val currentTrace = chatRepository.currentTrace

    val conversations: StateFlow<List<ConversationEntity>> =
        chatRepository.getConversations()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val workspaces: StateFlow<List<WorkspaceEntity>> =
        workspaceRepository.allWorkspaces
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val availableModels: StateFlow<List<AiModel>> =
        modelRepository.allModels
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** Live status for the official HCNSEC model catalogue refresh. */
    val isRefreshingModels = modelRepository.isLoading
    val modelRefreshError = modelRepository.errorMessage

    private var messagesObservationJob: Job? = null
    private var draftSaveJob: Job? = null

    // ---------------------------------------------------------------------
    // Streaming buffer
    //
    // Publishing a new ChatMessage for every token made the chat screen
    // recompose - and the markdown parser re-scan the whole answer - dozens of
    // times per second. Tokens are now accumulated here and flushed to the UI
    // at a fixed cadence, which removes the O(n^2) string growth and keeps the
    // frame budget free while the answer streams in.
    // ---------------------------------------------------------------------
    private val streamLock = Any()
    private val streamContent = StringBuilder()
    private val streamReasoning = StringBuilder()
    private var streamMessageId: String? = null
    private var streamConversationId: String? = null
    private var streamStartedAt = 0L
    private var lastFlushNanos = 0L
    private var pendingFlushJob: Job? = null

    private fun beginStreaming(conversationId: String) {
        pendingFlushJob?.cancel()
        pendingFlushJob = null
        synchronized(streamLock) {
            streamContent.setLength(0)
            streamReasoning.setLength(0)
        }
        lastFlushNanos = 0L
        streamStartedAt = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        streamMessageId = id
        streamConversationId = conversationId
        _streamingMessage.value = ChatMessage(
            id = id,
            conversationId = conversationId,
            role = "assistant",
            content = "",
            reasoningContent = null,
            timestamp = streamStartedAt,
            isStreaming = true
        )
    }

    private fun onStreamChunk(textChunk: String, reasoningChunk: String?) {
        synchronized(streamLock) {
            if (textChunk.isNotEmpty()) streamContent.append(textChunk)
            if (!reasoningChunk.isNullOrEmpty()) streamReasoning.append(reasoningChunk)
        }
        scheduleStreamFlush()
    }

    private fun scheduleStreamFlush() {
        val now = System.nanoTime()
        val elapsedMs = if (lastFlushNanos == 0L) Long.MAX_VALUE else (now - lastFlushNanos) / 1_000_000L
        if (elapsedMs >= STREAM_FLUSH_INTERVAL_MS) {
            publishStreamState()
        } else if (pendingFlushJob?.isActive != true) {
            pendingFlushJob = viewModelScope.launch {
                delay(STREAM_FLUSH_INTERVAL_MS - elapsedMs)
                publishStreamState()
            }
        }
    }

    private fun publishStreamState() {
        val id = streamMessageId ?: return
        val conversationId = streamConversationId ?: return
        lastFlushNanos = System.nanoTime()
        val content: String
        val reasoning: String?
        synchronized(streamLock) {
            content = streamContent.toString()
            reasoning = streamReasoning.toString().ifEmpty { null }
        }
        _streamingMessage.value = ChatMessage(
            id = id,
            conversationId = conversationId,
            role = "assistant",
            content = content,
            reasoningContent = reasoning,
            timestamp = streamStartedAt,
            isStreaming = true
        )
    }

    private fun endStreaming() {
        pendingFlushJob?.cancel()
        pendingFlushJob = null
        streamMessageId = null
        streamConversationId = null
        lastFlushNanos = 0L
        synchronized(streamLock) {
            streamContent.setLength(0)
            streamReasoning.setLength(0)
        }
        _streamingMessage.value = null
    }

    init {
        viewModelScope.launch {
            modelRepository.refreshModels()
        }
    }

    fun initConversation(conversationId: String?, workspaceId: String?) {
        _activeWorkspaceId.value = workspaceId
        if (conversationId != null && conversationId != _currentConversationId.value) {
            loadConversation(conversationId)
        } else if (_currentConversationId.value == null) {
            startNewConversation(workspaceId)
        }
    }

    fun loadConversation(conversationId: String) {
        _currentConversationId.value = conversationId
        _errorMessage.value = null
        messagesObservationJob?.cancel()
        messagesObservationJob = viewModelScope.launch {
            // Restore draft text if available
            val conv = chatRepository.getConversation(conversationId)
            _inputText.value = conv?.draftPrompt ?: ""

            chatRepository.getMessages(conversationId).collect { list ->
                _messages.value = list
            }
        }
    }

    fun startNewConversation(workspaceId: String? = _activeWorkspaceId.value) {
        viewModelScope.launch {
            val conv = chatRepository.createConversation(
                title = if (workspaceId != null) "Project Chat" else "New Conversation",
                workspaceId = workspaceId,
                model = _selectedModelId.value
            )
            loadConversation(conv.id)
            _attachments.value = emptyList()
            _inputText.value = ""
        }
    }

    fun onInputChanged(text: String) {
        _inputText.value = text
        // Auto-save user input draft to Room periodically
        val convId = _currentConversationId.value ?: return
        draftSaveJob?.cancel()
        draftSaveJob = viewModelScope.launch {
            kotlinx.coroutines.delay(400) // Debounce rapid keystrokes
            chatRepository.saveDraftPrompt(convId, if (text.isBlank()) null else text)
        }
    }

    fun selectModel(modelId: String) {
        _selectedModelId.value = modelId
        apiKeyRepository.setDefaultModel(modelId)
    }

    /** Fetches the models available to the currently configured HCNSEC key. */
    fun refreshAvailableModels() {
        viewModelScope.launch {
            modelRepository.refreshModels()
        }
    }

    fun addCustomModel(modelId: String) {
        viewModelScope.launch {
            modelRepository.addCustomModel(modelId)
            selectModel(modelId)
        }
    }

    fun deleteCustomModel(modelId: String) {
        viewModelScope.launch {
            modelRepository.deleteCustomModel(modelId)
            if (_selectedModelId.value == modelId) {
                selectModel("auto")
            }
        }
    }

    fun toggleFavoriteModel(model: AiModel) {
        viewModelScope.launch {
            modelRepository.toggleFavorite(model)
        }
    }

    fun attachFileFromUri(context: Context, uri: Uri) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val contentResolver = appContext.contentResolver
                    val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
                    val filename = getFileName(appContext, uri) ?: "attachment_${System.currentTimeMillis()}"
                    val isZip = filename.endsWith(".zip", ignoreCase = true) || mimeType.contains("zip")

                    if (isZip) {
                        val zipAttachment = AttachmentItem(
                            id = UUID.randomUUID().toString(),
                            name = filename,
                            mimeType = "application/zip",
                            sizeBytes = querySize(appContext, uri) ?: 0L,
                            isZipWorkspace = true,
                            status = AttachmentStatus.EXTRACTING
                        )
                        _attachments.value = _attachments.value + zipAttachment

                        val stream = contentResolver.openInputStream(uri)
                            ?: throw java.io.IOException("Could not open $filename")

                        // Stream the archive straight from the content provider. It used
                        // to be read fully into a byte[] (and then wrapped in a second
                        // in-memory stream), which doubled the peak memory of every upload.
                        val result = stream.use {
                            workspaceRepository.createWorkspaceFromZip(it, filename)
                        }
                        result.fold(
                            onSuccess = { summary ->
                                _activeWorkspaceId.value = summary.workspaceId
                                _attachments.value = _attachments.value.map {
                                    if (it.id == zipAttachment.id) {
                                        it.copy(
                                            workspaceId = summary.workspaceId,
                                            status = AttachmentStatus.READY,
                                            extractedText = summary.structureOverview
                                        )
                                    } else it
                                }
                            },
                            onFailure = { err ->
                                _attachments.value = _attachments.value.map {
                                    if (it.id == zipAttachment.id) {
                                        it.copy(status = AttachmentStatus.ERROR, error = err.message)
                                    } else it
                                }
                                _errorMessage.value = "ZIP extraction failed: ${err.message}"
                            }
                        )
                    } else if (mimeType == "application/pdf" || filename.endsWith(".pdf", ignoreCase = true)) {
                        val result = sourceDocumentRepository.importPdf(
                            uri = uri,
                            displayName = filename,
                            sizeBytes = querySize(appContext, uri) ?: 0L
                        )
                        result.fold(
                            onSuccess = { source ->
                                _attachments.value = _attachments.value + AttachmentItem(
                                    id = source.id,
                                    name = source.displayName,
                                    mimeType = source.mimeType,
                                    sizeBytes = source.sizeBytes,
                                    extractedText = source.extractedTextPath?.let { File(it).readText(Charsets.UTF_8) },
                                    status = AttachmentStatus.READY
                                )
                            },
                            onFailure = { throw it }
                        )
                    } else {
                        // Regular text/code/doc attachment (capped so a huge file
                        // cannot exhaust the heap of a low end device).
                        val textContent = contentResolver.openInputStream(uri)?.use { input ->
                            val out = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var total = 0L
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                val usable = minOf(read.toLong(), MAX_TEXT_ATTACHMENT_BYTES - total).toInt()
                                if (usable <= 0) break
                                out.write(buffer, 0, usable)
                                total += usable
                            }
                            String(out.toByteArray(), Charsets.UTF_8)
                        }

                        val item = AttachmentItem(
                            id = UUID.randomUUID().toString(),
                            name = filename,
                            mimeType = mimeType,
                            sizeBytes = querySize(appContext, uri) ?: textContent?.length?.toLong() ?: 0L,
                            extractedText = textContent,
                            status = AttachmentStatus.READY
                        )
                        _attachments.value = _attachments.value + item
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "Could not attach file: ${e.localizedMessage}"
            }
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = it.getString(index)
                }
            }
        }
        return name
    }

    private fun querySize(context: Context, uri: Uri): Long? {
        var size: Long? = null
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (index != -1 && !it.isNull(index)) {
                    size = it.getLong(index)
                }
            }
        }
        return size
    }

    fun removeAttachment(id: String) {
        _attachments.value = _attachments.value.filterNot { it.id == id }
    }

    fun sendMessage() {
        val text = _inputText.value.trim()
        val currentAttach = _attachments.value
        val convId = _currentConversationId.value ?: return

        if (text.isEmpty() && currentAttach.isEmpty()) return

        val promptToSend = if (text.isEmpty() && currentAttach.isNotEmpty()) {
            val firstZip = currentAttach.firstOrNull { it.isZipWorkspace }
            if (firstZip != null) {
                "Please analyze this uploaded workspace (${firstZip.name}), describe its architecture, key components, and entry points."
            } else {
                "Please analyze the attached file(s) (${currentAttach.joinToString { it.name }})."
            }
        } else {
            text
        }

        _inputText.value = ""
        _errorMessage.value = null

        // Clear persisted draft in Room
        viewModelScope.launch {
            chatRepository.saveDraftPrompt(convId, null)
        }

        beginStreaming(convId)

        viewModelScope.launch {
            try {
                chatRepository.sendMessage(
                    conversationId = convId,
                    userPrompt = promptToSend,
                    selectedModelId = _selectedModelId.value,
                    availableModels = availableModels.value,
                    attachments = currentAttach,
                    workspaceId = _activeWorkspaceId.value,
                    onChunkReceived = ::onStreamChunk,
                    onError = { err ->
                        endStreaming()
                        _errorMessage.value = err
                    },
                    onCompleted = {
                        endStreaming()
                        _attachments.value = emptyList()
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Surface the failure in the UI instead of letting an unhandled
                // coroutine exception take the whole process down.
                endStreaming()
                _errorMessage.value = e.localizedMessage ?: "Generation failed"
            }
        }
    }

    fun stopGeneration() {
        chatRepository.stopGeneration()
        endStreaming()
    }

    fun renameConversation(id: String, newTitle: String) {
        viewModelScope.launch {
            chatRepository.renameConversation(id, newTitle)
        }
    }

    fun togglePinConversation(id: String, currentPin: Boolean) {
        viewModelScope.launch {
            chatRepository.togglePinConversation(id, currentPin)
        }
    }

    fun regenerateLastResponse() {
        val currentMsgs = _messages.value
        val lastUserMsg = currentMsgs.findLast { it.role == "user" } ?: return
        val convId = _currentConversationId.value ?: return

        _errorMessage.value = null

        beginStreaming(convId)

        viewModelScope.launch {
            try {
                chatRepository.sendMessage(
                    conversationId = convId,
                    userPrompt = lastUserMsg.content,
                    selectedModelId = _selectedModelId.value,
                    availableModels = availableModels.value,
                    attachments = emptyList(),
                    workspaceId = _activeWorkspaceId.value,
                    // Regenerate: keep the original user message instead of
                    // appending a duplicate of it to the transcript.
                    regenerate = true,
                    onChunkReceived = ::onStreamChunk,
                    onError = { err ->
                        endStreaming()
                        _errorMessage.value = err
                    },
                    onCompleted = { endStreaming() }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                endStreaming()
                _errorMessage.value = e.localizedMessage ?: "Regeneration failed"
            }
        }
    }

    fun exportConversationAsMarkdown(): String {
        val convId = _currentConversationId.value
        val convTitle = conversations.value.find { it.id == convId }?.title ?: "Chat"
        val sb = StringBuilder()
        sb.append("# ").append(convTitle).append("\n\n")
        sb.append("> Exported from SALi-HCNSEC • Single-provider HCNSEC AI Agent\n\n")
        sb.append("---\n\n")

        for (msg in _messages.value) {
            val roleLabel = if (msg.role == "user") "### 👤 User" else "### 🤖 SALi-HCNSEC"
            sb.append(roleLabel).append("\n\n")
            if (!msg.reasoningContent.isNullOrBlank()) {
                sb.append("<details><summary>Reasoning Process</summary>\n\n")
                sb.append(msg.reasoningContent).append("\n\n")
                sb.append("</details>\n\n")
            }
            sb.append(msg.content).append("\n\n")
            sb.append("---\n\n")
        }

        return sb.toString()
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            chatRepository.deleteConversation(id)
            if (_currentConversationId.value == id) {
                _currentConversationId.value = null
                startNewConversation()
            }
        }
    }

    class Factory(
        private val chatRepository: ChatRepository,
        private val modelRepository: ModelRepository,
        private val workspaceRepository: WorkspaceRepository,
        private val sourceDocumentRepository: SourceDocumentRepository,
        private val apiKeyRepository: ApiKeyRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(chatRepository, modelRepository, workspaceRepository, sourceDocumentRepository, apiKeyRepository) as T
        }
    }
}
