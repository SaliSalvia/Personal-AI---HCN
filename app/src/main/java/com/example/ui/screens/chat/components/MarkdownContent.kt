package com.example.ui.screens.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.markdown.MarkdownBlock
import com.example.ui.markdown.MarkdownRenderer
import com.example.ui.theme.IceCyan
import com.example.ui.theme.IceCyanLight
import com.example.ui.theme.IceFrostBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletLight
import com.example.ui.theme.VioletPrimary

@Composable
fun MarkdownContent(
    content: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        val blocks = remember(content) { MarkdownRenderer.parseBlocks(content) }

        for (block in blocks) {
            when (block) {
                is MarkdownBlock.Code -> {
                    Spacer(modifier = Modifier.height(6.dp))
                    CodeBlockView(code = block.code, language = block.lang)
                    Spacer(modifier = Modifier.height(6.dp))
                }

                is MarkdownBlock.Header -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = block.text,
                        color = when (block.level) {
                            1 -> Color.White
                            2 -> IceCyanLight
                            else -> TextPrimary
                        },
                        fontSize = when (block.level) {
                            1 -> 20.sp
                            2 -> 17.sp
                            3 -> 15.sp
                            else -> 14.sp
                        },
                        fontWeight = when (block.level) {
                            1 -> FontWeight.ExtraBold
                            2 -> FontWeight.Bold
                            else -> FontWeight.SemiBold
                        },
                        lineHeight = when (block.level) {
                            1 -> 26.sp
                            2 -> 23.sp
                            else -> 20.sp
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                is MarkdownBlock.UnorderedList -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(modifier = Modifier.fillMaxWidth().padding(start = 4.dp)) {
                        for (itemText in block.items) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 7.dp, end = 10.dp)
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(IceCyan)
                                )
                                Text(
                                    text = MarkdownRenderer.renderInline(itemText),
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    lineHeight = 21.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                is MarkdownBlock.OrderedList -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(modifier = Modifier.fillMaxWidth().padding(start = 4.dp)) {
                        for ((num, itemText) in block.items) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "$num.",
                                    color = IceCyanLight,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    modifier = Modifier
                                        .width(26.dp)
                                        .padding(top = 1.dp)
                                )
                                Text(
                                    text = MarkdownRenderer.renderInline(itemText),
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    lineHeight = 21.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                is MarkdownBlock.BlockQuote -> {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x221E2238))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(22.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(VioletLight)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = MarkdownRenderer.renderInline(block.text),
                            color = Color(0xFFCBD5E1),
                            fontSize = 13.sp,
                            fontStyle = FontStyle.Italic,
                            lineHeight = 20.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                is MarkdownBlock.HorizontalRule -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(
                        color = Color(0x3338BDF8),
                        thickness = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = MarkdownRenderer.renderInline(block.text),
                        color = TextPrimary,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        modifier = Modifier.padding(vertical = 3.dp)
                    )
                }
            }
        }
    }
}
