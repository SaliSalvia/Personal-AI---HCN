package com.example.ui.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.example.ui.theme.IceCyan
import com.example.ui.theme.IceCyanLight
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.VioletLight
import com.example.ui.theme.VioletPrimary

sealed class MarkdownBlock {
    data class Code(val code: String, val lang: String) : MarkdownBlock()
    data class Header(val text: String, val level: Int) : MarkdownBlock()
    data class UnorderedList(val items: List<String>) : MarkdownBlock()
    data class OrderedList(val items: List<Pair<String, String>>) : MarkdownBlock() // (numberPrefix, text)
    data class BlockQuote(val text: String) : MarkdownBlock()
    data class HorizontalRule(val dummy: Unit = Unit) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
}

object MarkdownRenderer {

    /**
     * Parses markdown text into structured blocks with support for:
     * - Multi-line fenced code blocks with language specifiers
     * - Headers (# ## ### ####)
     * - Unordered bullet lists (- / * / +)
     * - Ordered numbered lists (1. 2. 3.)
     * - Block quotes (> quote)
     * - Horizontal rules (--- or ***)
     * - Normal paragraphs
     */
    fun parseBlocks(raw: String): List<MarkdownBlock> {
        val blocks = mutableListOf<MarkdownBlock>()
        val lines = raw.lines()
        var inCodeBlock = false
        var codeLang = ""
        val codeBuilder = StringBuilder()

        val currentUnorderedList = mutableListOf<String>()
        val currentOrderedList = mutableListOf<Pair<String, String>>()
        val currentQuoteLines = mutableListOf<String>()

        fun flushLists() {
            if (currentUnorderedList.isNotEmpty()) {
                blocks.add(MarkdownBlock.UnorderedList(currentUnorderedList.toList()))
                currentUnorderedList.clear()
            }
            if (currentOrderedList.isNotEmpty()) {
                blocks.add(MarkdownBlock.OrderedList(currentOrderedList.toList()))
                currentOrderedList.clear()
            }
            if (currentQuoteLines.isNotEmpty()) {
                blocks.add(MarkdownBlock.BlockQuote(currentQuoteLines.joinToString("\n")))
                currentQuoteLines.clear()
            }
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i]

            // Fenced code blocks
            if (line.trim().startsWith("```")) {
                flushLists()
                if (inCodeBlock) {
                    blocks.add(MarkdownBlock.Code(codeBuilder.toString().trimEnd(), codeLang))
                    codeBuilder.clear()
                    inCodeBlock = false
                } else {
                    inCodeBlock = true
                    codeLang = line.trim().removePrefix("```").trim()
                }
                i++
                continue
            }

            if (inCodeBlock) {
                if (codeBuilder.isNotEmpty()) codeBuilder.append("\n")
                codeBuilder.append(line)
                i++
                continue
            }

            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                flushLists()
                i++
                continue
            }

            // Horizontal rule
            if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
                flushLists()
                blocks.add(MarkdownBlock.HorizontalRule())
                i++
                continue
            }

            // Block quote
            if (trimmed.startsWith(">")) {
                if (currentUnorderedList.isNotEmpty() || currentOrderedList.isNotEmpty()) {
                    if (currentUnorderedList.isNotEmpty()) {
                        blocks.add(MarkdownBlock.UnorderedList(currentUnorderedList.toList()))
                        currentUnorderedList.clear()
                    }
                    if (currentOrderedList.isNotEmpty()) {
                        blocks.add(MarkdownBlock.OrderedList(currentOrderedList.toList()))
                        currentOrderedList.clear()
                    }
                }
                currentQuoteLines.add(trimmed.removePrefix(">").trim())
                i++
                continue
            } else if (currentQuoteLines.isNotEmpty()) {
                blocks.add(MarkdownBlock.BlockQuote(currentQuoteLines.joinToString("\n")))
                currentQuoteLines.clear()
            }

            // Headers
            if (trimmed.startsWith("#")) {
                flushLists()
                val level = trimmed.takeWhile { it == '#' }.length
                val headerText = trimmed.drop(level).trim()
                blocks.add(MarkdownBlock.Header(headerText, level.coerceIn(1, 6)))
                i++
                continue
            }

            // Unordered list items: -, *, +
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) {
                if (currentOrderedList.isNotEmpty()) {
                    blocks.add(MarkdownBlock.OrderedList(currentOrderedList.toList()))
                    currentOrderedList.clear()
                }
                currentUnorderedList.add(trimmed.substring(2).trim())
                i++
                continue
            }

            // Ordered list items: 1. 2. 10.
            val orderedMatch = Regex("^(\\d+)[.)]\\s+(.*)").find(trimmed)
            if (orderedMatch != null) {
                if (currentUnorderedList.isNotEmpty()) {
                    blocks.add(MarkdownBlock.UnorderedList(currentUnorderedList.toList()))
                    currentUnorderedList.clear()
                }
                val prefix = orderedMatch.groupValues[1]
                val content = orderedMatch.groupValues[2]
                currentOrderedList.add(prefix to content)
                i++
                continue
            }

            // Regular paragraph
            flushLists()
            blocks.add(MarkdownBlock.Paragraph(trimmed))
            i++
        }

        flushLists()

        if (inCodeBlock) {
            blocks.add(MarkdownBlock.Code(codeBuilder.toString().trimEnd(), codeLang))
        }

        return blocks
    }

    /**
     * Formats inline markdown with bold (** or __), italic (* or _), inline code (`...`),
     * strikethrough (~~...~~), and links ([text](url)).
     */
    fun renderInline(text: String): AnnotatedString {
        return buildAnnotatedString {
            var i = 0
            while (i < text.length) {
                // Inline code: `code`
                if (text[i] == '`') {
                    val endCode = text.indexOf('`', i + 1)
                    if (endCode != -1) {
                        val codeSnippet = text.substring(i + 1, endCode)
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = Color(0xFF1E2235),
                                color = IceCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        ) {
                            append(" $codeSnippet ")
                        }
                        i = endCode + 1
                        continue
                    }
                }

                // Bold text: **text** or __text__
                if ((text.startsWith("**", i) || text.startsWith("__", i)) && i + 2 < text.length) {
                    val delim = text.substring(i, i + 2)
                    val endBold = text.indexOf(delim, i + 2)
                    if (endBold != -1) {
                        val boldText = text.substring(i + 2, endBold)
                        withStyle(
                            SpanStyle(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        ) {
                            // Recursively allow inline code or italic inside bold
                            append(renderInline(boldText))
                        }
                        i = endBold + 2
                        continue
                    }
                }

                // Strikethrough: ~~text~~
                if (text.startsWith("~~", i) && i + 2 < text.length) {
                    val endStrike = text.indexOf("~~", i + 2)
                    if (endStrike != -1) {
                        val strikeText = text.substring(i + 2, endStrike)
                        withStyle(
                            SpanStyle(
                                textDecoration = TextDecoration.LineThrough,
                                color = Color(0xFF94A3B8)
                            )
                        ) {
                            append(renderInline(strikeText))
                        }
                        i = endStrike + 2
                        continue
                    }
                }

                // Italic text: *text* or _text_ (single delimiter, not followed by another delimiter)
                if ((text[i] == '*' || text[i] == '_') && (i == 0 || text[i - 1] != text[i]) && (i + 1 < text.length && text[i + 1] != text[i] && !text[i + 1].isWhitespace())) {
                    val delimChar = text[i]
                    val endItalic = text.indexOf(delimChar, i + 1)
                    if (endItalic != -1 && (endItalic + 1 == text.length || text[endItalic + 1] != delimChar)) {
                        val italicText = text.substring(i + 1, endItalic)
                        withStyle(
                            SpanStyle(
                                fontStyle = FontStyle.Italic,
                                color = VioletLight
                            )
                        ) {
                            append(renderInline(italicText))
                        }
                        i = endItalic + 1
                        continue
                    }
                }

                // Markdown Link: [label](url)
                if (text[i] == '[') {
                    val closeBracket = text.indexOf(']', i + 1)
                    if (closeBracket != -1 && closeBracket + 1 < text.length && text[closeBracket + 1] == '(') {
                        val closeParen = text.indexOf(')', closeBracket + 2)
                        if (closeParen != -1) {
                            val linkText = text.substring(i + 1, closeBracket)
                            withStyle(
                                SpanStyle(
                                    color = IceCyanLight,
                                    textDecoration = TextDecoration.Underline,
                                    fontWeight = FontWeight.SemiBold
                                )
                            ) {
                                append(linkText)
                            }
                            i = closeParen + 1
                            continue
                        }
                    }
                }

                append(text[i])
                i++
            }
        }
    }
}
