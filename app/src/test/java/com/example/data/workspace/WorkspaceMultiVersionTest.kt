package com.example.data.workspace

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WorkspaceMultiVersionTest {
    @Test
    fun multipleVersionsKeepCodeMarkdownDocxAndPdfInputs() = runBlocking {
        val manager = ZipWorkspaceManager(ApplicationProvider.getApplicationContext<Context>())
        val v1 = manager.extractZipToWorkspace(versionZip("v1", "legacy"), "project-v1.zip").getOrThrow()
        val v2 = manager.extractZipToWorkspace(versionZip("v2", "secure"), "project-v2.zip").getOrThrow()

        assertTrue(v1.workspaceId != v2.workspaceId)
        assertTrue(v1.totalFiles >= 5)
        assertTrue(v2.totalFiles >= 5)

        val v1Chunks = manager.getRelevantWorkspaceChunks(v1.workspaceId, "architecture secure documentation", 20)
        val v2Chunks = manager.getRelevantWorkspaceChunks(v2.workspaceId, "architecture secure documentation", 20)
        val combined = (v1Chunks + v2Chunks).joinToString("\n") { it.content }

        assertTrue(combined.contains("legacy") || combined.contains("secure"))
        assertTrue(combined.contains("DOCX evidence"))
        assertTrue(combined.contains("Markdown evidence"))
        assertTrue(v1Chunks.any { it.filePath.endsWith("report.pdf") })
        assertTrue(v2Chunks.any { it.filePath.endsWith("report.pdf") })
    }

    private fun versionZip(version: String, feature: String): java.io.InputStream {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            add(zip, "src/Main.kt", "fun main() = println(\"$feature\")")
            add(zip, "README.md", "Markdown evidence for $version architecture")
            add(zip, "docs/report.pdf", "PDF evidence for $version")
            addBytes(zip, "docs/notes.docx", docxBytes("DOCX evidence for $version"))
            add(zip, "config.json", "{\"version\":\"$version\",\"feature\":\"$feature\"}")
        }
        return output.toByteArray().inputStream()
    }

    private fun docxBytes(text: String): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { docx ->
            add(docx, "word/document.xml", "<w:document><w:body><w:p><w:r><w:t>$text</w:t></w:r></w:p></w:body></w:document>")
        }
        return output.toByteArray()
    }

    private fun addBytes(zip: ZipOutputStream, path: String, content: ByteArray) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(content)
        zip.closeEntry()
    }

    private fun add(zip: ZipOutputStream, path: String, content: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(content.toByteArray())
        zip.closeEntry()
    }
}
