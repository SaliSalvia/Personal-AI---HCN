package com.example.ui.screens.chat.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.ChatMessage
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.IceCyan
import com.example.ui.theme.IceCyanLight
import com.example.ui.theme.IceFrostBorder
import com.example.ui.theme.IceFrostBorderActive
import com.example.ui.theme.IceGlassBg
import com.example.ui.theme.IceUserBubbleBg
import com.example.ui.theme.IceUserBubbleBorder
import com.example.ui.theme.IceVioletBorder
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletLight
import com.example.ui.theme.VioletPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessageItem(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == "user"
    val context = LocalContext.current
    var isCopied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val timeLabel = remember(message.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
    }

    // Determine thinking state (like Claude: auto-expanded while thinking, collapsible once done)
    val isCurrentlyThinking = message.isStreaming && message.content.isBlank() && !message.reasoningContent.isNullOrBlank()
    var userToggledReasoning by remember(message.id) { mutableStateOf<Boolean?>(null) }
    val isReasoningExpanded = userToggledReasoning ?: isCurrentlyThinking

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .testTag("message_item_${message.id}"),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Top
        ) {
            // Assistant Avatar
            if (!isUser) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF14172B))
                        .border(1.dp, IceFrostBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app_launcher_frozen_mascot),
                        contentDescription = "SALi-HCNSEC",
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            // Message Body Bubble with Frosted Icy Corners
            val bubbleShape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            )
            val bubbleBorder = if (isUser) {
                BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        listOf(IceUserBubbleBorder, Color(0x33A78BFA), Color(0x1538BDF8))
                    )
                )
            } else {
                BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        listOf(IceFrostBorder, Color(0x187DD3FC), IceVioletBorder.copy(alpha = 0.25f))
                    )
                )
            }

            Surface(
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .clip(bubbleShape)
                    .border(bubbleBorder, bubbleShape),
                color = if (isUser) IceUserBubbleBg else IceGlassBg
            ) {
                Column(modifier = Modifier.padding(13.dp)) {
                    // 1. CLAUDE-STYLE THINKING PROCESS (Step-by-step reasoning from DeepSeek-R1 / HCNSEC)
                    if (!message.reasoningContent.isNullOrBlank()) {
                        ClaudeStyleThinkingBlock(
                            reasoningText = message.reasoningContent,
                            isCurrentlyThinking = isCurrentlyThinking,
                            isExpanded = isReasoningExpanded,
                            onToggleExpand = {
                                userToggledReasoning = !isReasoningExpanded
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    } else if (message.isStreaming && message.content.isBlank()) {
                        // Live initial thinking state before any tokens arrive
                        LiveThinkingWaitingPill()
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // 2. Main Message Content
                    if (isUser) {
                        Text(
                            text = message.content,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    } else {
                        if (message.content.isNotBlank()) {
                            MarkdownContent(content = message.content)
                        }
                    }
                }
            }
        }

        // Action / Meta Footer
        Row(
            modifier = Modifier
                .padding(start = if (isUser) 0.dp else 36.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = timeLabel,
                color = TextSecondary,
                fontSize = 10.sp
            )

            if (!isUser && message.content.isNotBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Copied Message", message.content)
                        clipboard.setPrimaryClip(clip)
                        isCopied = true
                        scope.launch {
                            delay(2000)
                            isCopied = false
                        }
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = if (isCopied) SuccessGreen else TextSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

/**
 * Claude-inspired thinking process container:
 * - Live auto-expanding during reasoning streaming
 * - Displays animated brain/sparkle status and word count
 * - Monospace quote block with left accent border
 * - Toggleable to collapse or inspect full reasoning chain anytime
 */
@Composable
private fun ClaudeStyleThinkingBlock(
    reasoningText: String,
    isCurrentlyThinking: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isThoughtCopied by remember { mutableStateOf(false) }

    val wordCount = remember(reasoningText) { countWords(reasoningText) }

    val blockShape = RoundedCornerShape(14.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(blockShape)
            .border(
                BorderStroke(
                    1.dp,
                    if (isCurrentlyThinking) IceFrostBorderActive else Color(0x2838BDF8)
                ),
                blockShape
            ),
        color = Color(0xB30B0E1B)
    ) {
        Column {
            // Header Bar (Clickable to Toggle)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(horizontal = 11.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isCurrentlyThinking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(13.dp),
                            color = IceCyan,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = "Thinking Process",
                            tint = IceCyanLight,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(7.dp))

                    Text(
                        text = if (isCurrentlyThinking) "Thinking..." else "Thought process",
                        color = if (isCurrentlyThinking) IceCyan else IceCyanLight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Word count or status badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x2638BDF8))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isCurrentlyThinking) "analyzing" else "$wordCount words",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse thoughts" else "Expand thoughts",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Expanded Thinking Body
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                ) {
                    // Left quote accent bar with reasoning text
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(16.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(if (isCurrentlyThinking) IceCyan else VioletPrimary)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "Inner Reasoning Chain",
                            color = VioletLight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x66080A12))
                            .padding(9.dp)
                    ) {
                        Text(
                            text = if (isCurrentlyThinking) "$reasoningText ▋" else reasoningText,
                            color = TextSecondary.copy(alpha = 0.9f),
                            fontSize = 11.5.sp,
                            lineHeight = 17.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Bottom thought actions (e.g., Copy Thought Chain)
                    if (!isCurrentlyThinking && reasoningText.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Thought Process", reasoningText)
                                        clipboard.setPrimaryClip(clip)
                                        isThoughtCopied = true
                                        scope.launch {
                                            delay(2000)
                                            isThoughtCopied = false
                                        }
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isThoughtCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                    contentDescription = "Copy thoughts",
                                    tint = if (isThoughtCopied) SuccessGreen else TextSecondary,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isThoughtCopied) "Copied" else "Copy thought process",
                                    color = if (isThoughtCopied) SuccessGreen else TextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Counts whitespace separated words without allocating a list of tokens.
 * The previous `split(regex).count {}` re-tokenised the entire reasoning chain on
 * every streamed update, which is quadratic over a long thought process.
 */
private fun countWords(text: String): Int {
    var count = 0
    var insideWord = false
    for (ch in text) {
        if (ch.isWhitespace()) {
            insideWord = false
        } else if (!insideWord) {
            insideWord = true
            count++
        }
    }
    return count
}

/**
 * Animated live indicator while the agent is initiating thought formulation
 */
@Composable
private fun LiveThinkingWaitingPill() {
    val infiniteTransition = rememberInfiniteTransition(label = "waiting_shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x3314172B))
            .border(1.dp, Color(0x2838BDF8), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(12.dp),
            color = IceCyan,
            strokeWidth = 1.5.dp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Thinking & formulating response...",
            color = IceCyanLight,
            fontSize = 12.sp,
            modifier = Modifier.alpha(alpha)
        )
    }
}
