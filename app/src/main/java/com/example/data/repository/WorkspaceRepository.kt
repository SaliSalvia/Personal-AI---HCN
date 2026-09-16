package com.example.data.repository

import com.example.data.local.dao.WorkspaceDao
import com.example.data.local.entity.WorkspaceEntity
import com.example.data.workspace.ZipWorkspaceManager
import com.example.domain.model.WorkspaceFileInfo
import com.example.domain.model.WorkspaceSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.InputStream

class WorkspaceRepository(
    private val workspaceDao: WorkspaceDao,
    private val zipWorkspaceManager: ZipWorkspaceManager
) {
    val allWorkspaces: Flow<List<WorkspaceEntity>> = workspaceDao.getAllWorkspaces()

    suspend fun createWorkspaceFromZip(
        inputStream: InputStream,
        workspaceName: String
    ): Result<WorkspaceSummary> = withContext(Dispatchers.IO) {
        val extractResult = zipWorkspaceManager.extractZipToWorkspace(inputStream, workspaceName)
        if (extractResult.isFailure) {
            return@withContext extractResult
        }

        val summary = extractResult.getOrThrow()
        val entity = WorkspaceEntity(
            id = summary.workspaceId,
            name = summary.projectName,
            rootPath = summary.workspaceId,
            projectType = summary.projectType,
            fileCount = summary.totalFiles,
            totalSizeBytes = summary.totalSizeBytes,
            summary = summary.structureOverview
        )
        workspaceDao.insert(entity)
        Result.success(summary)
    }

    suspend fun getWorkspace(id: String): WorkspaceEntity? = withContext(Dispatchers.IO) {
        workspaceDao.getWorkspaceById(id)
    }

    suspend fun getFileTree(workspaceId: String): List<WorkspaceFileInfo> = withContext(Dispatchers.IO) {
        zipWorkspaceManager.getFileTree(workspaceId)
    }

    suspend fun readFileContent(workspaceId: String, relativePath: String): String = withContext(Dispatchers.IO) {
        zipWorkspaceManager.readFileContent(workspaceId, relativePath)
    }

    suspend fun deleteWorkspace(workspaceId: String) = withContext(Dispatchers.IO) {
        zipWorkspaceManager.deleteWorkspace(workspaceId)
        workspaceDao.deleteById(workspaceId)
    }
}
