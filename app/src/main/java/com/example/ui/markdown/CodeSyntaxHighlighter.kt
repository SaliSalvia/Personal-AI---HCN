package com.example.ui.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.example.ui.theme.IceCyanLight
import com.example.ui.theme.VioletLight

/**
 * Syntax highlighting for common programming languages
 */
object CodeSyntaxHighlighter {

    private val KEYWORDS = setOf(
        "val", "var", "fun", "class", "interface", "object", "return", "if", "else",
        "when", "for", "while", "import", "package", "override", "private", "public",
        "protected", "internal", "data", "sealed", "suspend", "const", "companion",
        "true", "false", "null", "def", "async", "await", "from", "as", "try", "except",
        "finally", "let", "const", "function", "export", "default", "struct", "enum",
        "impl", "match", "mut", "pub", "self", "select", "where", "insert", "update", "delete"
    )

    private val TYPES = setOf(
        "String", "Int", "Boolean", "Double", "Float", "Long", "List", "Map", "Set",
        "Unit", "Any", "Array", "Flow", "StateFlow", "SharedFlow", "CoroutineScope",
        "Modifier", "Composable", "ViewModel", "Entity", "Dao", "Database", "Response"
    )

    fun highlightCode(code: String, language: String): androidx.compose.ui.text.AnnotatedString {
        return androidx.compose.ui.text.buildAnnotatedString {
            val lines = code.lines()
            for ((lineIndex, line) in lines.withIndex()) {
                var cursor = 0
                val trimmed = line.trimStart()

                // Check for single-line comments
                val commentIndex = when {
                    "//" in line -> line.indexOf("//")
                    "#" in line && (language in listOf("python", "py", "bash", "sh", "yaml", "yml")) -> line.indexOf("#")
                    else -> -1
                }

                while (cursor < line.length) {
                    if (commentIndex != -1 && cursor >= commentIndex) {
                        pushStyle(SpanStyle(color = Color(0xFF6272A4), fontStyle = FontStyle.Italic))
                        append(line.substring(cursor))
                        pop()
                        break
                    }

                    // Check for String literals
                    if (line[cursor] == '"' || line[cursor] == '\'') {
                        val quoteChar = line[cursor]
                        val nextQuote = line.indexOf(quoteChar, cursor + 1)
                        val endString = if (nextQuote != -1) nextQuote + 1 else line.length
                        pushStyle(SpanStyle(color = Color(0xFFA5D6A7))) // Soft green
                        append(line.substring(cursor, endString))
                        pop()
                        cursor = endString
                        continue
                    }

                    // Check for words (keywords, types, identifiers)
                    if (line[cursor].isLetter() || line[cursor] == '_') {
                        var endWord = cursor + 1
                        while (endWord < line.length && (line[endWord].isLetterOrDigit() || line[endWord] == '_')) {
                            endWord++
                        }
                        val word = line.substring(cursor, endWord)
                        when {
                            word in KEYWORDS -> {
                                pushStyle(SpanStyle(color = Color(0xFFFF79C6), fontWeight = FontWeight.SemiBold)) // Pink keyword
                                append(word)
                                pop()
                            }
                            word in TYPES -> {
                                pushStyle(SpanStyle(color = Color(0xFF8BE9FD), fontWeight = FontWeight.Medium)) // Cyan type
                                append(word)
                                pop()
                            }
                            word.matches(Regex("^[A-Z][a-zA-Z0-9]*$")) -> {
                                pushStyle(SpanStyle(color = Color(0xFF50FA7B))) // Green class/entity
                                append(word)
                                pop()
                            }
                            else -> {
                                append(word)
                            }
                        }
                        cursor = endWord
                        continue
                    }

                    // Check for numbers
                    if (line[cursor].isDigit()) {
                        var endNum = cursor + 1
                        while (endNum < line.length && (line[endNum].isDigit() || line[endNum] == '.' || line[endNum] == 'L' || line[endNum] == 'f')) {
                            endNum++
                        }
                        pushStyle(SpanStyle(color = Color(0xFFBD93F9))) // Purple number
                        append(line.substring(cursor, endNum))
                        pop()
                        cursor = endNum
                        continue
                    }

                    append(line[cursor])
                    cursor++
                }

                if (lineIndex < lines.size - 1) {
                    append("\n")
                }
            }
        }
    }
}
