package com.example.ui.screens.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.TraceStep
import com.example.domain.model.TraceStepStatus
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.IceCyan
import com.example.ui.theme.IceCyanLight
import com.example.ui.theme.IceFrostBorder
import com.example.ui.theme.IceGlassBg
import com.example.ui.theme.IceVioletBorder
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletLight
import com.example.ui.theme.VioletPrimary
import com.example.ui.localization.LocalAppStrings

@Composable
fun AgentTraceView(
    steps: List<TraceStep>,
    isGenerating: Boolean,
    modifier: Modifier = Modifier
) {
    if (steps.isEmpty()) return

    val strings = LocalAppStrings.current
    var isExpanded by remember { mutableStateOf(true) }
    val activeStep = steps.lastOrNull { it.status == TraceStepStatus.RUNNING }
        ?: steps.lastOrNull { it.status == TraceStepStatus.COMPLETED }

    val cardShape = RoundedCornerShape(16.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .border(
                BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        listOf(
                            IceFrostBorder,
                            Color(0x1838BDF8),
                            IceVioletBorder.copy(alpha = 0.25f)
                        )
                    )
                ),
                cardShape
            )
            .testTag("agent_trace_view"),
        color = IceGlassBg
    ) {
        Column {
            // Trace Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(0x2838BDF8)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = IceCyan,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Hub,
                                contentDescription = "Agent Trace",
                                tint = IceCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = if (isGenerating) strings.liveExecution else strings.executionReport,
                            color = TextPrimary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        if (activeStep != null && !isExpanded) {
                            Text(
                                text = "• ${activeStep.title}",
                                color = IceCyanLight,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x2638BDF8))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        val completedCount = steps.count { it.status == TraceStepStatus.COMPLETED }
                        Text(
                            text = strings.steps(completedCount, steps.size),
                            color = IceCyanLight,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Trace Details
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 12.dp, top = 2.dp)
                ) {
                    steps.forEachIndexed { index, step ->
                        TraceStepRow(
                            step = step,
                            isLast = index == steps.lastIndex
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TraceStepRow(
    step: TraceStep,
    isLast: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Status indicator column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(20.dp)
        ) {
            Box(
                modifier = Modifier.size(20.dp),
                contentAlignment = Alignment.Center
            ) {
                when (step.status) {
                    TraceStepStatus.COMPLETED -> {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Color(0x3310B981))
                                .border(1.dp, SuccessGreen.copy(alpha = 0.6f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Completed",
                                tint = SuccessGreen,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                    TraceStepStatus.RUNNING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = IceCyan,
                            strokeWidth = 2.dp
                        )
                    }
                    TraceStepStatus.FAILED -> {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Color(0x33EF4444))
                                .border(1.dp, ErrorRed.copy(alpha = 0.6f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Failed",
                                tint = ErrorRed,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                    TraceStepStatus.PENDING -> {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF334155))
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = step.title,
                color = when (step.status) {
                    TraceStepStatus.RUNNING -> IceCyan
                    TraceStepStatus.FAILED -> ErrorRed
                    TraceStepStatus.COMPLETED -> TextPrimary
                    TraceStepStatus.PENDING -> TextSecondary
                },
                fontSize = 12.sp,
                fontWeight = if (step.status == TraceStepStatus.RUNNING) FontWeight.SemiBold else FontWeight.Medium
            )

            if (!step.detail.isNullOrBlank()) {
                Text(
                    text = step.detail,
                    color = TextSecondary.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }
    }
}
