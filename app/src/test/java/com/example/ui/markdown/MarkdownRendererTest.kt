package com.example.ui.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownRendererTest {

    @Test
    fun testParseHeadersAndParagraphs() {
        val markdown = """
            # Header 1
            ## Header 2
            This is a regular paragraph.
        """.trimIndent()

        val blocks = MarkdownRenderer.parseBlocks(markdown)
        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Header)
        assertEquals(1, (blocks[0] as MarkdownBlock.Header).level)
        assertEquals("Header 1", (blocks[0] as MarkdownBlock.Header).text)

        assertTrue(blocks[1] is MarkdownBlock.Header)
        assertEquals(2, (blocks[1] as MarkdownBlock.Header).level)
        assertEquals("Header 2", (blocks[1] as MarkdownBlock.Header).text)

        assertTrue(blocks[2] is MarkdownBlock.Paragraph)
        assertEquals("This is a regular paragraph.", (blocks[2] as MarkdownBlock.Paragraph).text)
    }

    @Test
    fun testParseCodeBlock() {
        val markdown = """
            Here is some code:
            ```kotlin
            val x = 42
            println(x)
            ```
            Done.
        """.trimIndent()

        val blocks = MarkdownRenderer.parseBlocks(markdown)
        assertEquals(3, blocks.size)
        assertTrue(blocks[1] is MarkdownBlock.Code)
        val codeBlock = blocks[1] as MarkdownBlock.Code
        assertEquals("kotlin", codeBlock.lang)
        assertEquals("val x = 42\nprintln(x)", codeBlock.code)
    }

    @Test
    fun testParseLists() {
        val markdown = """
            - Bullet 1
            - Bullet 2
            
            1. Step one
            2. Step two
        """.trimIndent()

        val blocks = MarkdownRenderer.parseBlocks(markdown)
        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.UnorderedList)
        assertEquals(2, (blocks[0] as MarkdownBlock.UnorderedList).items.size)

        assertTrue(blocks[1] is MarkdownBlock.OrderedList)
        assertEquals(2, (blocks[1] as MarkdownBlock.OrderedList).items.size)
        assertEquals("1", (blocks[1] as MarkdownBlock.OrderedList).items[0].first)
        assertEquals("Step one", (blocks[1] as MarkdownBlock.OrderedList).items[0].second)
    }

    @Test
    fun testInlineRendering() {
        val text = "Hello **bold world** and `inline code` with *italic*!"
        val annotated = MarkdownRenderer.renderInline(text)
        assertEquals("Hello bold world and  inline code  with italic!", annotated.text)
    }
}
