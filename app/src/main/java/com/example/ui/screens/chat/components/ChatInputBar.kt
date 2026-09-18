package com.example.ui.screens.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.IceCyan
import com.example.ui.theme.IceCyanLight
import com.example.ui.theme.IceFrostBorder
import com.example.ui.theme.IceFrostBorderActive
import com.example.ui.theme.IceGlassBg
import com.example.ui.theme.IceGlassElevated
import com.example.ui.theme.IceVioletBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletLight
import com.example.ui.theme.VioletPrimary

@Composable
fun ChatInputBar(
    inputText: String,
    onInputChanged: (String) -> Unit,
    isGenerating: Boolean,
    selectedModelDisplay: String,
    onOpenModelSelector: () -> Unit,
    onAttachFile: () -> Unit,
    onAttachZip: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    hasAttachments: Boolean = false,
    modifier: Modifier = Modifier
) {
    val inputBarShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(inputBarShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        IceGlassElevated,
                        IceGlassBg
                    )
                )
            )
            .border(
                BorderStroke(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(IceFrostBorder, Color(0x1A38BDF8), IceVioletBorder)
                    )
                ),
                inputBarShape
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("chat_input_bar")
    ) {
        // Model Pill & Quick Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Model Selector Pill with Frosted Icy Edge
            val modelPillShape = RoundedCornerShape(20.dp)
            Surface(
                modifier = Modifier
                    .clip(modelPillShape)
                    .border(
                        BorderStroke(
                            1.dp,
                            Brush.horizontalGradient(
                                listOf(IceFrostBorderActive, IceVioletBorder)
                            )
                        ),
                        modelPillShape
                    )
                    .clickable { onOpenModelSelector() }
                    .testTag("model_selector_pill"),
                color = Color(0x331C243B)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Model",
                        tint = IceCyan,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = selectedModelDisplay,
                        color = IceCyanLight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Quick Zip Attachment Shortcut with Frosted Amber/Ice Rim
            val zipPillShape = RoundedCornerShape(20.dp)
            Surface(
                modifier = Modifier
                    .clip(zipPillShape)
                    .border(
                        BorderStroke(1.dp, Color(0x4DFBBF24)),
                        zipPillShape
                    )
                    .clickable { onAttachZip() }
                    .testTag("attach_zip_button"),
                color = Color(0x33261D12)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderZip,
                        contentDescription = "ZIP Workspace",
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Upload ZIP",
                        color = Color(0xFFFDE68A),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Input Field & Action Buttons Row with Icy Frosted Container
        val inputFieldShape = RoundedCornerShape(18.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(inputFieldShape)
                .background(Color(0x730D101C))
                .border(
                    BorderStroke(1.dp, Color(0x3338BDF8)),
                    inputFieldShape
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Attachment Icon
            IconButton(
                onClick = onAttachFile,
                modifier = Modifier
                    .size(38.dp)
                    .testTag("attach_file_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = "Attach file",
                    tint = IceCyan.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Text Input
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 36.dp, max = 120.dp)
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (inputText.isEmpty()) {
                    Text(
                        text = "پیام برای Salar Salvia…",
                        color = Color(0xFF70809C),
                        fontSize = 14.sp
                    )
                }
                BasicTextField(
                    value = inputText,
                    onValueChange = onInputChanged,
                    textStyle = TextStyle(
                        color = TextPrimary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Start,
                        textDirection = TextDirection.ContentOrRtl
                    ),
                    cursorBrush = SolidColor(IceCyan),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("chat_text_input")
                )
            }

            // Send / Stop Generation Button
            if (isGenerating) {
                IconButton(
                    onClick = onStop,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(ErrorRed.copy(alpha = 0.2f))
                        .border(1.dp, ErrorRed, CircleShape)
                        .testTag("stop_generation_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop generation",
                        tint = ErrorRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                val canSend = inputText.isNotBlank() || hasAttachments
                val sendButtonBrush = if (canSend) {
                    Brush.linearGradient(listOf(VioletPrimary, Color(0xFF6366F1), IceCyan))
                } else {
                    Brush.linearGradient(listOf(Color(0xFF1E2133), Color(0xFF1A1D2B)))
                }

                IconButton(
                    onClick = {
                        if (canSend) onSend()
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(sendButtonBrush)
                        .border(
                            BorderStroke(
                                1.dp,
                                if (canSend) IceFrostBorderActive else Color(0x2238BDF8)
                            ),
                            CircleShape
                        )
                        .testTag("send_button"),
                    enabled = canSend
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send message",
                        tint = if (canSend) Color.White else Color(0xFF64748B),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
