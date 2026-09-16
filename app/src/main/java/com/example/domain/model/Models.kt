package com.example.domain.model

enum class ModelCapability {
    REASONING,
    CODING,
    VISION,
    LARGE_CONTEXT,
    FAST_CHAT,
    CREATIVE
}

data class AiModel(
    val id: String,
    val displayName: String,
    val isCustom: Boolean = false,
    val isFavorite: Boolean = false,
    val capabilities: Set<ModelCapability> = emptySet(),
    val description: String = ""
)

enum class TaskCategory(val label: String) {
    GENERAL_CHAT("General Conversation"),
    REASONING("Deep Reasoning & Math"),
    CODING("Code Engineering"),
    DOCUMENT_ANALYSIS("Document Analysis"),
    LARGE_CONTEXT("Large Context Analysis"),
    VISION("Vision & Multimodal"),
    CREATIVE("Creative Writing"),
    SUMMARIZATION("Summarization"),
    STRUCTURED_EXTRACTION("Data Extraction"),
    FILE_PROJECT_ANALYSIS("Workspace Project Analysis")
}

data class ChatMessage(
    val id: String,
    val conversationId: String,
    val role: String, // "user", "assistant", "system"
    val content: String,
    val reasoningContent: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val attachments: List<AttachmentItem> = emptyList(),
    val tokenCount: Int? = null,
    val isStreaming: Boolean = false
)

data class AttachmentItem(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val extractedText: String? = null,
    val isZipWorkspace: Boolean = false,
    val workspaceId: String? = null,
    val status: AttachmentStatus = AttachmentStatus.READY,
    val error: String? = null
)

enum class AttachmentStatus {
    PENDING,
    EXTRACTING,
    INDEXING,
    READY,
    ERROR
}

enum class TraceStepStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}

data class TraceStep(
    val id: String,
    val title: String,
    val detail: String? = null,
    val status: TraceStepStatus = TraceStepStatus.PENDING
)

data class AgentTrace(
    val steps: List<TraceStep> = emptyList(),
    val isVisible: Boolean = false
)
