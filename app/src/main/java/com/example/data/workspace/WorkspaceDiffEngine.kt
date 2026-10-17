package com.example.data.workspace

import com.example.domain.model.WorkspaceFileChange
import com.example.domain.model.WorkspaceVersionDiff
import java.io.File
import java.security.MessageDigest

/**
 * Computes a deterministic file-level comparison without asking an AI model to infer
 * which files changed. Hashes are calculated from file bytes, not timestamps or names.
 *
 * The caller supplies workspace IDs for provenance; this class never mutates either tree.
 */
class WorkspaceDiffEngine(
    private val ignoredDirectories: Set<String> = DEFAULT_IGNORED_DIRECTORIES,
    private val maxFilesPerWorkspace: Int = 100_000
) {
    fun compare(
        leftWorkspaceId: String,
        leftRoot: File,
        rightWorkspaceId: String,
        rightRoot: File
    ): WorkspaceVersionDiff {
        require(leftRoot.isDirectory) { "Left workspace root does not exist" }
        require(rightRoot.isDirectory) { "Right workspace root does not exist" }
        require(maxFilesPerWorkspace > 0) { "maxFilesPerWorkspace must be positive" }

        val left = manifest(leftRoot)
        val right = manifest(rightRoot)
        val added = mutableListOf<WorkspaceFileChange>()
        val removed = mutableListOf<WorkspaceFileChange>()
        val changed = mutableListOf<WorkspaceFileChange>()
        var unchanged = 0

        (left.keys + right.keys).toSortedSet().forEach { path ->
            val old = left[path]
            val new = right[path]
            when {
                old == null && new != null -> added += WorkspaceFileChange(
                    relativePath = path,
                    rightSha256 = new.sha256,
                    rightSizeBytes = new.sizeBytes
                )
                old != null && new == null -> removed += WorkspaceFileChange(
                    relativePath = path,
                    leftSha256 = old.sha256,
                    leftSizeBytes = old.sizeBytes
                )
                old != null && new != null && old.sha256 != new.sha256 -> changed += WorkspaceFileChange(
                    relativePath = path,
                    leftSha256 = old.sha256,
                    rightSha256 = new.sha256,
                    leftSizeBytes = old.sizeBytes,
                    rightSizeBytes = new.sizeBytes
                )
                else -> unchanged++
            }
        }

        return WorkspaceVersionDiff(
            leftWorkspaceId = leftWorkspaceId,
            rightWorkspaceId = rightWorkspaceId,
            added = added,
            removed = removed,
            changed = changed,
            unchangedCount = unchanged
        )
    }

    private fun manifest(root: File): Map<String, FileFingerprint> {
        val result = sortedMapOf<String, FileFingerprint>()
        root.walkTopDown()
            .onEnter { directory ->
                directory == root || directory.name !in ignoredDirectories
            }
            .filter { it.isFile }
            .forEach { file ->
                val relativePath = file.relativeTo(root).invariantSeparatorsPath
                if (relativePath.split('/').any { it in ignoredDirectories }) return@forEach
                if (result.size >= maxFilesPerWorkspace) {
                    throw IllegalStateException("Workspace exceeds file comparison limit ($maxFilesPerWorkspace)")
                }
                result[relativePath] = FileFingerprint(sha256(file), file.length())
            }
        return result
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private data class FileFingerprint(val sha256: String, val sizeBytes: Long)

    companion object {
        val DEFAULT_IGNORED_DIRECTORIES = setOf(
            ".git", ".gradle", "build", "node_modules", ".idea",
            "__pycache__", ".vscode", "target", "bin", ".dart_tool", "dist"
        )
    }
}
