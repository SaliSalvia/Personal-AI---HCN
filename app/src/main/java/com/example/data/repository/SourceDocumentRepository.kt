package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.local.dao.SourceDocumentDao
import com.example.data.local.entity.SourceDocumentEntity
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** App-private, durable source library for PDFs and imported files. */
class SourceDocumentRepository(
    private val context: Context,
    private val documentDao: SourceDocumentDao
) {
    val documents: Flow<List<SourceDocumentEntity>> = documentDao.observeAll()

    suspend fun importPdf(uri: Uri, displayName: String, sizeBytes: Long): Result<SourceDocumentEntity> =
        withContext(Dispatchers.IO) {
            try {
                val id = UUID.randomUUID().toString()
                val root = File(context.filesDir, "sali_sources").apply { mkdirs() }
                val pdfFile = File(root, "$id.pdf")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    pdfFile.outputStream().use { input.copyTo(it) }
                } ?: error("Could not open PDF")

                PDFBoxResourceLoader.init(context)
                val extractedText = PDDocument.load(pdfFile).use { document ->
                    PDFTextStripper().getText(document)
                }.take(MAX_EXTRACTED_CHARS)
                val textFile = File(root, "$id.txt").apply { writeText(extractedText, Charsets.UTF_8) }
                val entity = SourceDocumentEntity(
                    id = id,
                    displayName = displayName,
                    mimeType = "application/pdf",
                    kind = KIND_PDF,
                    localPath = pdfFile.absolutePath,
                    extractedTextPath = textFile.absolutePath,
                    sizeBytes = sizeBytes
                )
                documentDao.insert(entity)
                Result.success(entity)
            } catch (error: Exception) {
                Result.failure(error)
            }
        }

    /** Returns compact, relevant extracts for several previously imported PDFs. */
    suspend fun relevantText(documentIds: List<String>, query: String, maxChars: Int = 18_000): String =
        withContext(Dispatchers.IO) {
            val terms = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+"))
                .filter { it.length >= 3 }.toSet()
            val out = StringBuilder()
            documentDao.getByIds(documentIds).forEach { source ->
                val text = source.extractedTextPath?.let(::File)?.takeIf(File::exists)
                    ?.readText(Charsets.UTF_8).orEmpty()
                if (text.isBlank()) return@forEach
                val excerpt = text.lineSequence()
                    .filter { line -> terms.isEmpty() || terms.any { line.contains(it, ignoreCase = true) } }
                    .take(80).joinToString("\n").ifBlank { text.take(4_000) }
                if (out.length < maxChars) {
                    out.append("\n\n--- PDF: ${source.displayName} ---\n")
                    out.append(excerpt.take(maxChars - out.length))
                }
            }
            out.toString()
        }

    suspend fun delete(document: SourceDocumentEntity) = withContext(Dispatchers.IO) {
        File(document.localPath).delete()
        document.extractedTextPath?.let(::File)?.delete()
        documentDao.deleteById(document.id)
    }

    private companion object {
        const val KIND_PDF = "pdf"
        const val MAX_EXTRACTED_CHARS = 2_000_000
    }
}
