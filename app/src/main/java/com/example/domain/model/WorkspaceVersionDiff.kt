package com.example.domain.model

/** Deterministic, content-hash-based comparison between two imported project snapshots. */
data class WorkspaceVersionDiff(
    val leftWorkspaceId: String,
    val rightWorkspaceId: String,
    val added: List<WorkspaceFileChange>,
    val removed: List<WorkspaceFileChange>,
    val changed: List<WorkspaceFileChange>,
    val unchangedCount: Int,
    val ignoredFileCount: Int = 0
) {
    val changedCount: Int get() = added.size + removed.size + changed.size
}

data class WorkspaceFileChange(
    val relativePath: String,
    val leftSha256: String? = null,
    val rightSha256: String? = null,
    val leftSizeBytes: Long? = null,
    val rightSizeBytes: Long? = null
)
