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
            File(repoRoot, "app/src/main/java"),
            File("src/main/java"),
            File("app/src/main/java"),
            File("../src/main/java"),
            File("../app/src/main/java"),
        )
        candidates.firstOrNull { it.isDirectory }
            ?: error("Could not locate the module sources. cwd=${File("").absolutePath}")
    }

    /**
     * Repository root, found by walking up for the settings script.
     *
     * The module directory contains its own `.gitignore` (`/build`), so a plain `.gitignore` walk
     * would stop at `app/` when the tests run with the module as their working directory.
     */
    val repoRoot: File by lazy {
        var current: File? = File("").absoluteFile
        var dotGit: File? = null
        while (current != null) {
            if (File(current, "settings.gradle.kts").isFile) return@lazy current
            if (dotGit == null && File(current, ".git").isDirectory) dotGit = current
            current = current.parentFile
        }
        dotGit ?: error("Could not locate the repository root from ${File("").absolutePath}")
    }

    /**
     * Comment-free view of a source file.
     *
     * Token audits must judge executable code: a comment that *describes* a forbidden construct
     * (for example a note that no TLS bypass exists) is documentation, not a violation.
     */
    fun code(text: String): String {
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '/' && i + 1 < text.length && text[i + 1] == '/' -> {
                    while (i < text.length && text[i] != '\n') i++
                }

                c == '/' && i + 1 < text.length && text[i + 1] == '*' -> {
                    i += 2
                    while (i + 1 < text.length && !(text[i] == '*' && text[i + 1] == '/')) i++
                    i = (i + 2).coerceAtMost(text.length)
                }

                c == '"' -> {
                    out.append('"')
                    i++
                    while (i < text.length && text[i] != '"' && text[i] != '\n') {
                        if (text[i] == '\\') i++
                        i++
                    }
                    out.append('"')
                    if (i < text.length && text[i] == '"') i++
                }

                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        return out.toString()
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
