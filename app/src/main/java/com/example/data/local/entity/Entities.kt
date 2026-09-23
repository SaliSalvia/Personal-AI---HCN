package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val workspaceId: String? = null,
    val selectedModel: String = "auto",
    val draftPrompt: String? = null
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["conversationId"])]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String, // "user", "assistant", "system"
    val content: String,
    val reasoningContent: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val attachmentsJson: String? = null,
    val tokenCount: Int? = null
)

@Entity(tableName = "workspaces")
data class WorkspaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val rootPath: String,
    val projectType: String = "Unknown",
    val fileCount: Int = 0,
    val totalSizeBytes: Long = 0L,
    val summary: String? = null,
    val archivePath: String? = null,
    val archiveSha256: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "custom_models")
data class CustomModelEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/** Metadata for an imported source. Content lives in app-private storage, not in Room. */
@Entity(tableName = "source_documents", indices = [Index(value = ["createdAt"])])
data class SourceDocumentEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val mimeType: String,
    val kind: String,
    val localPath: String,
    val extractedTextPath: String? = null,
    val workspaceId: String? = null,
    val sizeBytes: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)
