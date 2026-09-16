package com.example.security

import java.io.File

/**
 * Shared helpers for the static security/boundary audits.
 *
 * These tests read the repository sources so that structural rules (no plaintext endpoints, no
 * logging of credentials, no direct secure-storage access in provider code) are enforced by the
 * build rather than by review alone.
 */
internal object SourceAudit {

    /** `app/src/main/java` of the Android module, resolved relative to the test working directory. */
    val mainSourceRoot: File by lazy {
        val candidates = listOf(
            File("src/main/java"),
            File("app/src/main/java"),
            File("../src/main/java"),
            File("../app/src/main/java"),
        )
        candidates.firstOrNull { it.isDirectory }
            ?: error("Could not locate the module sources. cwd=${File("").absolutePath}")
    }

    /** Repository root, found by walking up until `.gitignore` is seen. */
    val repoRoot: File by lazy {
        var current: File? = File("").absoluteFile
        while (current != null) {
            if (File(current, ".gitignore").isFile) return@lazy current
            current = current.parentFile
        }
        error("Could not locate the repository root from ${File("").absolutePath}")
    }

    fun kotlinSources(root: File = mainSourceRoot): List<File> =
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    fun relativePath(file: File, root: File = mainSourceRoot): String =
        file.relativeTo(root).path.replace(File.separatorChar, '/')

    fun read(relativePath: String, root: File = mainSourceRoot): String =
        File(root, relativePath).readText()

    /** All `.kt` sources whose path contains [segment]. */
    fun sourcesIn(segment: String, root: File = mainSourceRoot): List<File> =
        kotlinSources(root).filter { relativePath(it, root).contains(segment) }
}
