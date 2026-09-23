package com.example.domain.model

import java.io.File

data class WorkspaceFileInfo(
    val relativePath: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val extension: String,
    val children: List<WorkspaceFileInfo> = emptyList(),
    val isImportantProjectFile: Boolean = false
)

data class WorkspaceSummary(
    val workspaceId: String,
    val projectName: String,
    val projectType: String,
    val totalFiles: Int,
    val totalDirectories: Int,
    val totalSizeBytes: Long,
    val keyFiles: List<String>,
    val fileExtensionsDistribution: Map<String, Int>,
    val structureOverview: String,
    val archivePath: String? = null,
    val archiveSha256: String? = null
)

data class FileChunk(
    val filePath: String,
    val chunkIndex: Int,
    val totalChunks: Int,
    val content: String
)
