package com.example.data.workspace

import android.content.Context
import com.example.domain.model.FileChunk
import com.example.domain.model.WorkspaceFileInfo
import com.example.domain.model.WorkspaceSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ZipWorkspaceManager(private val context: Context) {

    companion object {
        private const val MAX_TOTAL_UNCOMPRESSED_BYTES = 150 * 1024 * 1024L // 150 MB safety cap
        private const val MAX_ENTRY_SIZE_BYTES = 25 * 1024 * 1024L // 25 MB max per file
        private const val MAX_ENTRIES_COUNT = 10_000
        private const val CHUNK_SIZE_CHARS = 3_000

        private val IGNORED_DIRECTORIES = setOf(
            ".git", ".gradle", "build", "node_modules", ".idea",
            "__pycache__", ".vscode", "target", "bin", ".dart_tool", "dist"
        )

        private val IGNORED_EXTENSIONS = setOf(
            "class", "dex", "so", "o", "obj", "exe", "apk", "aab", "jar",
            "pyc", "pyo", "lock", "png", "jpg", "jpeg", "webp", "gif", "ico"
        )

        private val CODE_EXTENSIONS = setOf(
            "kt", "kts", "java", "py", "js", "ts", "jsx", "tsx", "c", "cpp", "h",
            "cs", "go", "rs", "swift", "dart", "html", "css", "xml", "json", "yaml",
            "yml", "md", "sql", "sh", "gradle", "properties", "toml", "env"
        )
    }

    private val workspacesDir: File
        get() = File(context.filesDir, "sali_workspaces").apply { if (!exists()) mkdirs() }

    /**
     * Safely extracts a ZIP input stream to a new workspace folder with Zip Slip protection.
     */
    suspend fun extractZipToWorkspace(
        inputStream: InputStream,
        workspaceName: String
    ): Result<WorkspaceSummary> = withContext(Dispatchers.IO) {
        val workspaceId = UUID.randomUUID().toString()
        val destDir = File(workspacesDir, workspaceId)
        if (!destDir.mkdirs()) {
            return@withContext Result.failure(Exception("Failed to create workspace directory"))
        }

        val canonicalDestDirPath = destDir.canonicalPath + File.separator
        var totalBytesExtracted = 0L
        var totalEntries = 0

        try {
            ZipInputStream(inputStream).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    totalEntries++
                    if (totalEntries > MAX_ENTRIES_COUNT) {
                        throw SecurityException("ZIP archive contains too many entries (exceeds $MAX_ENTRIES_COUNT)")
                    }

                    val entryFile = File(destDir, entry.name)
                    val canonicalEntryPath = entryFile.canonicalPath

                    // CRITICAL ZIP SLIP / PATH TRAVERSAL CHECK
                    if (!canonicalEntryPath.startsWith(canonicalDestDirPath) && canonicalEntryPath != destDir.canonicalPath) {
                        throw SecurityException("Zip Slip vulnerability detected: '${entry.name}' attempts directory traversal")
                    }

                    if (entry.isDirectory) {
                        entryFile.mkdirs()
                    } else {
                        entryFile.parentFile?.mkdirs()

                        var entryBytes = 0L
                        entryFile.outputStream().buffered().use { output ->
                            val buffer = ByteArray(8192)
                            var len: Int
                            while (zis.read(buffer).also { len = it } != -1) {
                                entryBytes += len
                                totalBytesExtracted += len

                                if (entryBytes > MAX_ENTRY_SIZE_BYTES) {
                                    throw SecurityException("ZIP entry '${entry.name}' exceeds maximum file size ($MAX_ENTRY_SIZE_BYTES bytes)")
                                }
                                if (totalBytesExtracted > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                                    throw SecurityException("Archive uncompressed size exceeds maximum allowance ($MAX_TOTAL_UNCOMPRESSED_BYTES bytes)")
                                }

                                output.write(buffer, 0, len)
                            }
                        }
                    }

                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // Analyze extracted workspace
            val summary = analyzeWorkspace(workspaceId, destDir, workspaceName)
            Result.success(summary)
        } catch (e: Exception) {
            // Clean up partially extracted files on failure
            destDir.deleteRecursively()
            Result.failure(e)
        }
    }

    /**
     * Analyzes project structure, builds file tree and classifies project type.
     */
    fun analyzeWorkspace(workspaceId: String, rootDir: File, name: String): WorkspaceSummary {
        val extensionCounts = mutableMapOf<String, Int>()
        val keyFiles = mutableListOf<String>()
        var totalFiles = 0
        var totalDirs = 0
        var totalSize = 0L

        rootDir.walkTopDown().forEach { file ->
            val relPath = file.relativeTo(rootDir).path
            val pathSegments = relPath.split(File.separator)
            val shouldIgnore = pathSegments.any { it in IGNORED_DIRECTORIES }

            if (!shouldIgnore) {
                if (file.isDirectory) {
                    totalDirs++
                } else {
                    totalFiles++
                    totalSize += file.length()
                    val ext = file.extension.lowercase()
                    if (ext.isNotEmpty()) {
                        extensionCounts[ext] = (extensionCounts[ext] ?: 0) + 1
                    }

                    // Collect key project indicators
                    val filename = file.name
                    if (isKeyProjectFile(filename)) {
                        keyFiles.add(relPath)
                    }
                }
            }
        }

        val projectType = detectProjectType(keyFiles, extensionCounts)
        val overview = buildStructureOverview(projectType, totalFiles, totalDirs, keyFiles, extensionCounts)

        return WorkspaceSummary(
            workspaceId = workspaceId,
            projectName = name.removeSuffix(".zip"),
            projectType = projectType,
            totalFiles = totalFiles,
            totalDirectories = totalDirs,
            totalSizeBytes = totalSize,
            keyFiles = keyFiles.take(15),
            fileExtensionsDistribution = extensionCounts,
            structureOverview = overview
        )
    }

    private fun isKeyProjectFile(name: String): Boolean {
        return name in setOf(
            "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts",
            "AndroidManifest.xml", "package.json", "tsconfig.json", "requirements.txt",
            "setup.py", "pyproject.toml", "pom.xml", "pubspec.yaml", "Cargo.toml",
            "go.mod", "Makefile", "Dockerfile", "README.md"
        )
    }

    fun detectProjectType(keyFiles: List<String>, extensions: Map<String, Int>): String {
        val filenames = keyFiles.map { File(it).name }

        return when {
            filenames.contains("AndroidManifest.xml") || filenames.any { it.contains("gradle") && (extensions["kt"] ?: 0) > 0 } -> "Android (Kotlin)"
            filenames.contains("pubspec.yaml") -> "Flutter / Dart"
            filenames.contains("package.json") && (extensions["tsx"] ?: 0) > 0 -> "React / TypeScript"
            filenames.contains("package.json") && (extensions["jsx"] ?: 0) > 0 -> "React / JavaScript"
            filenames.contains("package.json") -> "Node.js"
            filenames.contains("requirements.txt") || filenames.contains("pyproject.toml") || (extensions["py"] ?: 0) > 0 -> "Python"
            filenames.contains("Cargo.toml") || (extensions["rs"] ?: 0) > 0 -> "Rust"
            filenames.contains("go.mod") || (extensions["go"] ?: 0) > 0 -> "Go"
            filenames.contains("pom.xml") || (extensions["java"] ?: 0) > 0 -> "Java / Maven"
            (extensions["kt"] ?: 0) > 0 -> "Kotlin"
            (extensions["c"] ?: 0) > 0 || (extensions["cpp"] ?: 0) > 0 -> "C / C++"
            else -> "General Software Project"
        }
    }

    private fun buildStructureOverview(
        type: String,
        files: Int,
        dirs: Int,
        keyFiles: List<String>,
        extensions: Map<String, Int>
    ): String {
        val topExts = extensions.entries.sortedByDescending { it.value }.take(5)
            .joinToString { ".${it.key} (${it.value})" }
        val keys = if (keyFiles.isNotEmpty()) keyFiles.take(6).joinToString(", ") else "None detected"

        return "Architecture: $type\nFiles: $files, Folders: $dirs\nKey files: $keys\nDominant languages: $topExts"
    }

    /**
     * Builds the hierarchical file tree for the Workspace UI browser.
     */
    fun getFileTree(workspaceId: String, currentDir: File? = null): List<WorkspaceFileInfo> {
        val root = currentDir ?: File(workspacesDir, workspaceId)
        if (!root.exists()) return emptyList()

        val workspaceRoot = File(workspacesDir, workspaceId)
        val files = root.listFiles().orEmpty()
            .filter { file ->
                !file.name.startsWith(".") && file.name !in IGNORED_DIRECTORIES
            }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

        return files.map { file ->
            val relPath = file.relativeTo(workspaceRoot).path
            WorkspaceFileInfo(
                relativePath = relPath,
                name = file.name,
                isDirectory = file.isDirectory,
                sizeBytes = if (file.isDirectory) 0L else file.length(),
                extension = file.extension.lowercase(),
                children = if (file.isDirectory) getFileTree(workspaceId, file) else emptyList(),
                isImportantProjectFile = isKeyProjectFile(file.name)
            )
        }
    }

    /**
     * Reads text from a specific workspace file safely.
     */
    fun readFileContent(workspaceId: String, relativePath: String, maxChars: Int = 100_000): String {
        val workspaceRoot = File(workspacesDir, workspaceId)
        val target = File(workspaceRoot, relativePath)

        if (!target.canonicalPath.startsWith(workspaceRoot.canonicalPath + File.separator)) {
            return "Error: Invalid file path access"
        }
        if (!target.exists() || target.isDirectory) {
            return "Error: File does not exist"
        }
        if (target.length() > 5 * 1024 * 1024) {
            return "File too large to display directly in preview (${target.length() / 1024} KB)."
        }

        return try {
            val content = target.readText(Charsets.UTF_8)
            if (content.length > maxChars) {
                content.take(maxChars) + "\n\n... [Content truncated for display]"
            } else {
                content
            }
        } catch (e: Exception) {
            "Binary or unreadable file format (${e.localizedMessage})"
        }
    }

    /**
     * Indexes workspace code files into chunks for relevant context retrieval.
     */
    fun getRelevantWorkspaceChunks(
        workspaceId: String,
        userQuery: String,
        maxTotalChunks: Int = 5
    ): List<FileChunk> {
        val workspaceRoot = File(workspacesDir, workspaceId)
        if (!workspaceRoot.exists()) return emptyList()

        val queryTerms = userQuery.lowercase().split(" ", ",", ".", "_", "-", "/", "\\")
            .filter { it.length >= 3 }

        val chunks = mutableListOf<FileChunk>()

        workspaceRoot.walkTopDown()
            .filter { file ->
                !file.isDirectory &&
                        file.extension.lowercase() in CODE_EXTENSIONS &&
                        !file.relativeTo(workspaceRoot).path.split(File.separator).any { it in IGNORED_DIRECTORIES }
            }
            .forEach { file ->
                if (chunks.size >= maxTotalChunks) return@forEach

                try {
                    val content = file.readText(Charsets.UTF_8)
                    val relPath = file.relativeTo(workspaceRoot).path

                    // Score relevance
                    val matchesQuery = queryTerms.any { term ->
                        relPath.lowercase().contains(term) || content.lowercase().contains(term)
                    }

                    if (matchesQuery || isKeyProjectFile(file.name)) {
                        val fileChunks = chunkString(content, CHUNK_SIZE_CHARS)
                        for (i in fileChunks.indices) {
                            if (chunks.size < maxTotalChunks) {
                                chunks.add(
                                    FileChunk(
                                        filePath = relPath,
                                        chunkIndex = i + 1,
                                        totalChunks = fileChunks.size,
                                        content = fileChunks[i]
                                    )
                                )
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

        return chunks
    }

    private fun chunkString(text: String, chunkSize: Int): List<String> {
        if (text.length <= chunkSize) return listOf(text)
        val list = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val end = (start + chunkSize).coerceAtMost(text.length)
            list.add(text.substring(start, end))
            start = end
        }
        return list
    }

    fun deleteWorkspace(workspaceId: String) {
        val dir = File(workspacesDir, workspaceId)
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }

    /** Exports one or more imported workspaces to a shareable ZIP in cache storage. */
    fun exportWorkspaces(workspaceIds: List<String>, archiveName: String): File {
        require(workspaceIds.isNotEmpty()) { "Select at least one workspace" }
        val output = File(context.cacheDir, "${archiveName.ifBlank { "Salar-Salvia-golden" }}.zip")
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            workspaceIds.distinct().forEach { id ->
                val root = File(workspacesDir, id)
                require(root.exists()) { "Workspace is no longer available" }
                root.walkTopDown().filter { it.isFile }.forEach { file ->
                    val relative = file.relativeTo(root).invariantSeparatorsPath
                    val entry = "$id/$relative"
                    zip.putNextEntry(ZipEntry(entry))
                    file.inputStream().buffered().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        return output
    }
}
