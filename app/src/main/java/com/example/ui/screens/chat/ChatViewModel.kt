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
import com.example.data.security.ApiKeyRepository
import com.example.domain.model.AiModel
import com.example.domain.model.AttachmentItem
import com.example.domain.model.AttachmentStatus
import com.example.domain.model.ChatMessage
import com.example.domain.model.TraceStep
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val modelRepository: ModelRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val apiKeyRepository: ApiKeyRepository
) : ViewModel() {

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

    private var messagesObservationJob: Job? = null
    private var draftSaveJob: Job? = null

    // NOTE (Phase 4.2 §14): there is deliberately no init-time model refresh here. Opening the app
    // must not contact the provider: the model catalogue is fetched only from user-triggered
    // entry points (see refreshModelsIfNeeded), and chats are sent only when the user sends one.

    /**
     * Refreshes the HCNSEC model catalogue on demand — safe to call from user-driven UI events.
     *
     * Skips the request when a catalogue is already cached, so repeatedly opening the model
     * selector does not produce repeated traffic.
     */
    fun refreshModelsIfNeeded() {
        if (modelRepository.apiModels.value.isNotEmpty()) return
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
        viewModelScope.launch {
            try {
                val contentResolver = context.contentResolver
                val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
                val filename = getFileName(context, uri) ?: "attachment_${System.currentTimeMillis()}"

                val inputStream = contentResolver.openInputStream(uri) ?: return@launch
                val bytes = inputStream.readBytes()
                val size = bytes.size.toLong()

                // Check if it's a ZIP archive
                if (filename.endsWith(".zip", ignoreCase = true) || mimeType.contains("zip")) {
                    val zipAttachment = AttachmentItem(
                        id = UUID.randomUUID().toString(),
                        name = filename,
                        mimeType = "application/zip",
                        sizeBytes = size,
                        isZipWorkspace = true,
                        status = AttachmentStatus.EXTRACTING
                    )
                    _attachments.value = _attachments.value + zipAttachment

                    // Extract and create workspace
                    val stream = bytes.inputStream()
                    val result = workspaceRepository.createWorkspaceFromZip(stream, filename)
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
                } else {
                    // Regular text/code/doc attachment
                    val textContent = try {
                        String(bytes, Charsets.UTF_8)
                    } catch (_: Exception) {
                        null
                    }

                    val item = AttachmentItem(
                        id = UUID.randomUUID().toString(),
                        name = filename,
                        mimeType = mimeType,
                        sizeBytes = size,
                        extractedText = textContent,
                        status = AttachmentStatus.READY
                    )
                    _attachments.value = _attachments.value + item
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

        val streamingMsgId = UUID.randomUUID().toString()
        _streamingMessage.value = ChatMessage(
            id = streamingMsgId,
            conversationId = convId,
            role = "assistant",
            content = "",
            reasoningContent = null,
            isStreaming = true
        )

        viewModelScope.launch {
            chatRepository.sendMessage(
                conversationId = convId,
                userPrompt = promptToSend,
                selectedModelId = _selectedModelId.value,
                availableModels = availableModels.value,
                attachments = currentAttach,
                workspaceId = _activeWorkspaceId.value,
                onChunkReceived = { textChunk, reasoningChunk ->
                    val current = _streamingMessage.value
                    if (current != null) {
                        val newContent = current.content + textChunk
                        val newReasoning = if (reasoningChunk != null) {
                            (current.reasoningContent ?: "") + reasoningChunk
                        } else current.reasoningContent

                        _streamingMessage.value = current.copy(
                            content = newContent,
                            reasoningContent = newReasoning
                        )
                    }
                },
                onError = { err ->
                    _streamingMessage.value = null
                    _errorMessage.value = err
                },
                onCompleted = {
                    _streamingMessage.value = null
                    _attachments.value = emptyList()
                }
            )
        }
    }

    fun stopGeneration() {
        chatRepository.stopGeneration()
        _streamingMessage.value = null
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

        val streamingMsgId = UUID.randomUUID().toString()
        _streamingMessage.value = ChatMessage(
            id = streamingMsgId,
            conversationId = convId,
            role = "assistant",
            content = "",
            reasoningContent = null,
            isStreaming = true
        )

        viewModelScope.launch {
            chatRepository.sendMessage(
                conversationId = convId,
                userPrompt = lastUserMsg.content,
                selectedModelId = _selectedModelId.value,
                availableModels = availableModels.value,
                attachments = emptyList(),
                workspaceId = _activeWorkspaceId.value,
                onChunkReceived = { textChunk, reasoningChunk ->
                    val current = _streamingMessage.value
                    if (current != null) {
                        val newContent = current.content + textChunk
                        val newReasoning = if (reasoningChunk != null) {
                            (current.reasoningContent ?: "") + reasoningChunk
                        } else current.reasoningContent

                        _streamingMessage.value = current.copy(
                            content = newContent,
                            reasoningContent = newReasoning
                        )
                    }
                },
                onError = { err ->
                    _streamingMessage.value = null
                    _errorMessage.value = err
                },
                onCompleted = {
                    _streamingMessage.value = null
                }
            )
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
        private val apiKeyRepository: ApiKeyRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(chatRepository, modelRepository, workspaceRepository, apiKeyRepository) as T
        }
    }
}
