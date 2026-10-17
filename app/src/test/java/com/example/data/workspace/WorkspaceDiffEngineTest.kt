package com.example.data.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class WorkspaceDiffEngineTest {
    @Test
    fun reportsAddedRemovedChangedAndUnchangedFilesDeterministically() {
        val left = Files.createTempDirectory("sali-left").toFile()
        val right = Files.createTempDirectory("sali-right").toFile()
        try {
            left.resolve("src").mkdirs()
            right.resolve("src").mkdirs()
            left.resolve("src/keep.kt").writeText("same")
            right.resolve("src/keep.kt").writeText("same")
            left.resolve("src/change.kt").writeText("old implementation")
            right.resolve("src/change.kt").writeText("new implementation")
            left.resolve("remove.md").writeText("removed")
            right.resolve("added.md").writeText("added")
            left.resolve(".git").mkdirs()
            left.resolve(".git/config").writeText("ignored metadata")
            right.resolve(".git").mkdirs()
            right.resolve(".git/config").writeText("different ignored metadata")

            val diff = WorkspaceDiffEngine().compare("v1", left, "v2", right)

            assertEquals(listOf("added.md"), diff.added.map { it.relativePath })
            assertEquals(listOf("remove.md"), diff.removed.map { it.relativePath })
            assertEquals(listOf("src/change.kt"), diff.changed.map { it.relativePath })
            assertEquals(1, diff.unchangedCount)
            assertEquals("v1", diff.leftWorkspaceId)
            assertEquals("v2", diff.rightWorkspaceId)
            assertTrue(diff.changed.single().leftSha256 != diff.changed.single().rightSha256)
        } finally {
            left.deleteRecursively()
            right.deleteRecursively()
        }
    }

    @Test
    fun rejectsSymbolicLinkThatEscapesWorkspaceRoot() {
        val root = Files.createTempDirectory("sali-symlink-root").toFile()
        val outside = Files.createTempFile("sali-outside", ".txt")
        try {
            Files.writeString(outside, "must not be read")
            val link = root.toPath().resolve("external.txt")
            try {
                Files.createSymbolicLink(link, outside)
            } catch (e: UnsupportedOperationException) {
                return
            } catch (e: java.io.IOException) {
                // Some constrained environments disable symlink creation.
                return
            } catch (e: SecurityException) {
                return
            }

            val result = runCatching {
                WorkspaceDiffEngine().compare("v1", root, "v2", root)
            }
            assertTrue("Comparison must reject symlinks", result.isFailure)
        } finally {
            root.deleteRecursively()
            Files.deleteIfExists(outside)
        }
    }

    @Test
    fun rejectsMissingWorkspaceRoot() {
        val missing = Files.createTempDirectory("sali-missing").toFile().resolve("not-created")
        val existing = Files.createTempDirectory("sali-existing").toFile()
        try {
            val result = runCatching {
                WorkspaceDiffEngine().compare("v1", missing, "v2", existing)
            }
            assertTrue(result.isFailure)
        } finally {
            existing.deleteRecursively()
            missing.parentFile?.deleteRecursively()
        }
    }
}
