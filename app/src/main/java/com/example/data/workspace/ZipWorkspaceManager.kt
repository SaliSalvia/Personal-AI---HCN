package com.example.data.workspace

import android.content.Context
import com.example.domain.model.FileChunk
import com.example.domain.model.WorkspaceFileInfo
import com.example.domain.model.WorkspaceSummary
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ZipWorkspaceManager(private val context: Context) {

    companion object {
        private const val MAX_TOTAL_UNCOMPRESSED_BYTES = 150 * 1024 * 1024L // 150 MB safety cap
        private const val MAX_COMPRESSED_ARCHIVE_BYTES = 200 * 1024 * 1024L // 200 MB persisted upload cap
        // Protect phones from a 20-archive lab exhausting internal storage.
        private const val MAX_TOTAL_PROJECT_LAB_BYTES = 1024L * 1024L * 1024L // 1 GiB including originals
        private const val MAX_ENTRY_SIZE_BYTES = 25 * 1024 * 1024L // 25 MB max per file
        private const val MAX_ENTRIES_COUNT = 10_000
        private const val MAX_COMPRESSION_RATIO = 1_000L
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
            "yml", "md", "sql", "sh", "gradle", "properties", "toml", "env", "docx", "pdf"
        )
    }

    private val workspacesDir: File
        get() = File(context.filesDir, "sali_workspaces").apply { if (!exists()) mkdirs() }

    /** The original uploaded archives are retained separately from extracted files. */
    private val archivesDir: File
        get() = File(context.filesDir, "sali_archives").apply { if (!exists()) mkdirs() }

    /**
     * Safely stores the original ZIP and extracts it to a new workspace folder with
     * Zip Slip, entry-count, and decompressed-size protection.
     */
    suspend fun extractZipToWorkspace(
        inputStream: InputStream,
        workspaceName: String
    ): Result<WorkspaceSummary> = withContext(Dispatchers.IO) {
        val workspaceId = UUID.randomUUID().toString()
        val destDir = File(workspacesDir, workspaceId)
        val archiveFile = File(archivesDir, "$workspaceId.zip")
        if (!destDir.mkdirs()) {
            return@withContext Result.failure(Exception("Failed to create workspace directory"))
        }

        val canonicalDestDirPath = destDir.canonicalPath + File.separator
        val existingStoredBytes = calculateStoredProjectBytes(excludingWorkspaceId = workspaceId)
        var totalBytesExtracted = 0L
        var totalEntries = 0
        var archiveBytes = 0L
        // ZIP archives may contain duplicate names that overwrite earlier entries.
        // Track normalized names so extraction is deterministic and auditable.
        val seenEntryNames = mutableSetOf<String>()

        try {
            val digest = MessageDigest.getInstance("SHA-256")
            archiveFile.outputStream().buffered().use { output ->
                val buffer = ByteArray(8192)
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    archiveBytes += read
                    if (archiveBytes > MAX_COMPRESSED_ARCHIVE_BYTES) {
                        throw SecurityException("ZIP archive exceeds maximum upload size ($MAX_COMPRESSED_ARCHIVE_BYTES bytes)")
                    }
                    if (existingStoredBytes + archiveBytes > MAX_TOTAL_PROJECT_LAB_BYTES) {
                        throw SecurityException("Project Lab storage would exceed the 1 GiB total limit. Remove older project versions before importing more ZIPs.")
                    }
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                }
            }
            val archiveSha256 = digest.digest().toHexString()

            ZipInputStream(FileInputStream(archiveFile)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    totalEntries++
                    if (totalEntries > MAX_ENTRIES_COUNT) {
                        throw SecurityException("ZIP archive contains too many entries (exceeds $MAX_ENTRIES_COUNT)")
                    }

                    // Normalize both slash styles before validating. Reject absolute paths,
                    // drive-prefixed paths, NULs, traversal segments, and duplicate entries.
                    val normalizedName = entry.name.replace('\\', '/')
                    val pathSegments = normalizedName.split('/')
                    if (normalizedName.isBlank() ||
                        normalizedName.startsWith('/') ||
                        normalizedName.contains("//") ||
                        Regex("^[A-Za-z]:").containsMatchIn(normalizedName) ||
                        normalizedName.contains('\u0000') ||
                        pathSegments.any { it == ".." || it == "." }
                    ) {
                        throw SecurityException("Unsafe ZIP entry path: '${entry.name}'")
                    }
                    val dedupeKey = normalizedName.trimEnd('/')
                    if (!seenEntryNames.add(dedupeKey)) {
                        throw SecurityException("ZIP contains duplicate or colliding entry path: '${entry.name}'")
                    }

                    val entryFile = File(destDir, normalizedName)
                    val canonicalEntryPath = entryFile.canonicalPath

                    // Defense in depth: verify the resolved destination remains inside the workspace.
                    if (!canonicalEntryPath.startsWith(canonicalDestDirPath)) {
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
                                if (existingStoredBytes + archiveBytes + totalBytesExtracted > MAX_TOTAL_PROJECT_LAB_BYTES) {
                                    throw SecurityException("Project Lab storage would exceed the 1 GiB total limit. Remove older project versions before importing more ZIPs.")
                                }

                                output.write(buffer, 0, len)
                            }
                        }
                    }

                    // Some ZIP implementations expose compressedSize only after the entry
                    // is fully consumed. Enforce a conservative ratio limit in addition to
                    // absolute expanded-byte caps to flag ZIP-bomb-like payloads.
                    val compressedSize = entry.compressedSize
                    val expandedSize = entry.size
                    if (compressedSize > 0L && expandedSize > 0L &&
                        expandedSize / compressedSize.coerceAtLeast(1L) > MAX_COMPRESSION_RATIO
                    ) {
                        throw SecurityException("ZIP entry '${entry.name}' exceeds the allowed compression ratio")
                    }

                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // Analyze extracted workspace. The archive metadata is retained so the
            // original upload can be reused/exported and its integrity can be checked.
            val summary = analyzeWorkspace(workspaceId, destDir, workspaceName).copy(
                archivePath = archiveFile.absolutePath,
                archiveSha256 = archiveSha256
            )
            Result.success(summary)
        } catch (e: Exception) {
            // Clean up partially extracted files on failure, including the durable copy.
            destDir.deleteRecursively()
            archiveFile.delete()
            Result.failure(e)
        }
    }

    /** Includes original archives and extracted files in the aggregate Project Lab storage budget. */
    private fun calculateStoredProjectBytes(excludingWorkspaceId: String): Long {
        val excludedArchiveName = "$excludingWorkspaceId.zip"
        val archiveBytes = archivesDir.listFiles().orEmpty()
            .filter { it.isFile && it.name != excludedArchiveName }
            .sumOf { it.length() }
        val excludedRootPrefix = File(workspacesDir, excludingWorkspaceId).absolutePath + File.separator
        val extractedBytes = workspacesDir.walkTopDown()
            .filter { it.isFile && !it.absolutePath.startsWith(excludedRootPrefix) }
            .sumOf { it.length() }
        return archiveBytes + extractedBytes
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
                    val content = readTextLikeFile(file)
                    val relPath = file.relativeTo(workspaceRoot).path

                    // Score relevance
                    val matchesQuery = queryTerms.any { term ->
                        relPath.lowercase().contains(term) || content.lowercase().contains(term)
                    }

                    val isDocumentation = file.extension.lowercase() in setOf("md", "docx", "pdf")
                    if (matchesQuery || isKeyProjectFile(file.name) || isDocumentation) {
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

    /**
     * Builds a bounded, labelled context for a serious project review. It includes
     * the persisted workspace summary, file inventory, and more chunks than the
     * lightweight chat retrieval path, while remaining safe for model context limits.
     */
    /**
     * Builds a fair, bounded context for a project lab containing up to 20 versions.
     * Every candidate gets a manifest entry before source excerpts are budgeted, so
     * early ZIPs cannot consume the entire prompt and hide later candidates.
     */
    fun buildDeepAnalysisContext(
        workspaceIds: List<String>,
        userQuery: String,
        maxChunksPerWorkspace: Int = 24,
        maxTotalChars: Int = 120_000
    ): String {
        data class VersionContext(
            val id: String,
            val index: Int,
            val root: File,
            val summary: WorkspaceSummary,
            val chunks: List<FileChunk>
        )

        val ids = workspaceIds.distinct().take(20)
        val versions = ids.mapIndexedNotNull { index, id ->
            val root = File(workspacesDir, id)
            if (!root.isDirectory) return@mapIndexedNotNull null
            VersionContext(
                id = id,
                index = index + 1,
                root = root,
                summary = analyzeWorkspace(id, root, "Stored project version ${index + 1}"),
                chunks = getRelevantWorkspaceChunks(id, userQuery, maxChunksPerWorkspace)
            )
        }

        val output = StringBuilder("--- PROJECT LAB: FAIR MULTI-VERSION CONTEXT ---\n")
        output.append("Candidates requested: ${workspaceIds.distinct().size}; candidates analyzed: ${versions.size}; maximum supported: 20.\n")
        output.append("Evidence excerpts are budgeted per candidate. A missing excerpt is not evidence that a file or feature is absent.\n")

        // First include concise metadata for every candidate, independent of source excerpt budget.
        versions.forEach { version ->
            val summary = version.summary
            output.append("\n### VERSION ${version.index}: ${version.id}\n")
            output.append("Project type: ${summary.projectType}\n")
            output.append("Files: ${summary.totalFiles}; directories: ${summary.totalDirectories}; size: ${summary.totalSizeBytes} bytes\n")
            output.append("Key files: ${summary.keyFiles.take(10).joinToString(", ").ifBlank { "none detected" }}\n")
            output.append("Top extensions: ${summary.fileExtensionsDistribution.entries.sortedByDescending { it.value }.take(8).joinToString { ".${it.key}=${it.value}" }}\n")
        }

        val remainingBudget = (maxTotalChars - output.length).coerceAtLeast(0)
        val perVersionBudget = if (versions.isEmpty()) 0 else remainingBudget / versions.size

        versions.forEach { version ->
            if (output.length >= maxTotalChars) return@forEach
            val evidence = StringBuilder("\n--- EVIDENCE EXCERPTS: VERSION ${version.index} [${version.id}] ---\n")
            var includedChunks = 0
            for (chunk in version.chunks) {
                val block = "\nFILE ${chunk.filePath} [chunk ${chunk.chunkIndex}/${chunk.totalChunks}]\n${chunk.content}\n"
                val remainingForVersion = perVersionBudget - evidence.length
                if (remainingForVersion <= 0) break
                if (block.length > remainingForVersion) {
                    if (includedChunks == 0 && remainingForVersion > 120) {
                        evidence.append(block.take(remainingForVersion - 24))
                        evidence.append("\n[excerpt truncated to preserve fair version coverage]\n")
                    }
                    break
                }
                evidence.append(block)
                includedChunks++
            }
            evidence.append("Evidence coverage: ${includedChunks} complete excerpt(s) included from ${version.chunks.size} selected excerpt(s).\n")
            val room = maxTotalChars - output.length
            if (room > 0) output.append(evidence.take(room))
        }

        return output.toString().take(maxTotalChars)
    }

    private fun readTextLikeFile(file: File): String {
        if (file.name.endsWith(".pdf", ignoreCase = true)) {
            return try {
                PDFBoxResourceLoader.init(context)
                PDDocument.load(file).use { document -> PDFTextStripper().getText(document).take(200_000) }
            } catch (_: Exception) {
                "[PDF text extraction failed for ${file.name}]"
            }
        }
        if (!file.name.endsWith(".docx", ignoreCase = true)) return file.readText(Charsets.UTF_8)
        val xml = ZipInputStream(file.inputStream()).use { zip ->
            var document: String? = null
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    document = readZipEntryTextBounded(zip, 4 * 1024 * 1024L)
                    break
                }
                entry = zip.nextEntry
            }
            document.orEmpty()
        }
        return xml.replace(Regex("</w:p>"), "\\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .trim()
    }

    private fun readZipEntryTextBounded(zip: ZipInputStream, maxBytes: Long): String {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0L
        while (total < maxBytes) {
            val read = zip.read(buffer)
            if (read == -1) break
            val usable = minOf(read.toLong(), maxBytes - total).toInt()
            output.write(buffer, 0, usable)
            total += usable
        }
        return output.toString(Charsets.UTF_8.name())
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
        File(workspacesDir, workspaceId).takeIf(File::exists)?.deleteRecursively()
        File(archivesDir, "$workspaceId.zip").takeIf(File::exists)?.delete()
    }

    fun getStoredArchive(workspaceId: String): File? =
        File(archivesDir, "$workspaceId.zip").takeIf { it.isFile }

    /** Compares two imported snapshots without mutating either original workspace. */
    fun compareWorkspaces(leftWorkspaceId: String, rightWorkspaceId: String) =
        WorkspaceDiffEngine().compare(
            leftWorkspaceId = leftWorkspaceId,
            leftRoot = File(workspacesDir, leftWorkspaceId),
            rightWorkspaceId = rightWorkspaceId,
            rightRoot = File(workspacesDir, rightWorkspaceId)
        )

    private fun ByteArray.toHexString(): String = joinToString("") { byte -> "%02x".format(byte) }

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
