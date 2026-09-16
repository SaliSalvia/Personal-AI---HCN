package com.example.ui.screens.chat.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.markdown.CodeSyntaxHighlighter
import com.example.ui.theme.CodeBg
import com.example.ui.theme.IceCyan
import com.example.ui.theme.IceFrostBorder
import com.example.ui.theme.IceVioletBorder
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletLight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CodeBlockView(
    code: String,
    language: String = "text",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isCopied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val blockShape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(blockShape)
            .background(CodeBg)
            .border(
                BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        listOf(
                            IceFrostBorder.copy(alpha = 0.45f),
                            Color(0x1838BDF8),
                            IceVioletBorder.copy(alpha = 0.25f)
                        )
                    )
                ),
                blockShape
            )
    ) {
        // Code Block Top Bar with Frosted Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xE6101322))
                .border(
                    BorderStroke(0.5.dp, IceFrostBorder.copy(alpha = 0.2f))
                )
                .padding(horizontal = 14.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = language.ifBlank { "code" }.lowercase(),
                color = IceCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isCopied) {
                    Text(
                        text = "Copied",
                        color = SuccessGreen,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }

                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Copied code", code)
                        clipboard.setPrimaryClip(clip)
                        isCopied = true
                        scope.launch {
                            delay(2000)
                            isCopied = false
                        }
                    },
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("copy_code_button")
                ) {
                    Icon(
                        imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        tint = if (isCopied) SuccessGreen else TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Code Content with Syntax Highlighting
        val highlightedCode = remember(code, language) {
            CodeSyntaxHighlighter.highlightCode(code, language)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(14.dp)
        ) {
            Text(
                text = highlightedCode,
                color = TextPrimary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
