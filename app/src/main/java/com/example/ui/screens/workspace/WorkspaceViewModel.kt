package com.example.ui.screens.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.WorkspaceEntity
import com.example.data.repository.WorkspaceRepository
import com.example.domain.model.WorkspaceFileInfo
import com.example.domain.model.WorkspaceVersionDiff
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.asStateFlow

class WorkspaceViewModel(
    private val workspaceId: String,
    private val workspaceRepository: WorkspaceRepository
) : ViewModel() {

    private val _workspace = MutableStateFlow<WorkspaceEntity?>(null)
    val workspace = _workspace.asStateFlow()

    private val _fileTree = MutableStateFlow<List<WorkspaceFileInfo>>(emptyList())
    val fileTree = _fileTree.asStateFlow()

    private val _selectedFilePath = MutableStateFlow<String?>(null)
    val selectedFilePath = _selectedFilePath.asStateFlow()

    private val _selectedFileContent = MutableStateFlow<String?>(null)
    val selectedFileContent = _selectedFileContent.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    val workspaces = workspaceRepository.allWorkspaces

    private val _versionDiff = MutableStateFlow<WorkspaceVersionDiff?>(null)
    val versionDiff = _versionDiff.asStateFlow()

    private val _isComparingVersions = MutableStateFlow(false)
    val isComparingVersions = _isComparingVersions.asStateFlow()

    private val _comparisonError = MutableStateFlow<String?>(null)
    val comparisonError = _comparisonError.asStateFlow()

    fun compareVersions(leftWorkspaceId: String, rightWorkspaceId: String) {
        if (leftWorkspaceId == rightWorkspaceId) {
            _comparisonError.value = "Choose two different project versions."
            _versionDiff.value = null
            return
        }
        viewModelScope.launch {
            _isComparingVersions.value = true
            _comparisonError.value = null
            _versionDiff.value = null
            try {
                _versionDiff.value = workspaceRepository.compareWorkspaces(leftWorkspaceId, rightWorkspaceId)
            } catch (error: Exception) {
                _comparisonError.value = error.message ?: "Could not compare these project versions."
            } finally {
                _isComparingVersions.value = false
            }
        }
    }

    init {
        loadWorkspace()
    }

    fun loadWorkspace() {
        viewModelScope.launch {
            _workspace.value = workspaceRepository.getWorkspace(workspaceId)
            _fileTree.value = workspaceRepository.getFileTree(workspaceId)
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun selectFile(relativePath: String) {
        _selectedFilePath.value = relativePath
        viewModelScope.launch {
            val content = workspaceRepository.readFileContent(workspaceId, relativePath)
            _selectedFileContent.value = content
        }
    }

    fun clearSelectedFile() {
        _selectedFilePath.value = null
        _selectedFileContent.value = null
    }

    fun deleteWorkspace(onDeleted: () -> Unit) {
        viewModelScope.launch {
            workspaceRepository.deleteWorkspace(workspaceId)
            onDeleted()
        }
    }

    class Factory(
        private val workspaceId: String,
        private val workspaceRepository: WorkspaceRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return WorkspaceViewModel(workspaceId, workspaceRepository) as T
        }
    }
}
