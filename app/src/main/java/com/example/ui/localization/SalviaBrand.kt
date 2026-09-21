package com.example.ui.localization

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.IceCyan
import com.example.ui.theme.IceFrostBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletPrimary

val SalviaMagenta = Color(0xFFD946EF)
val SalviaGold = Color(0xFFF3C77B)

@Composable
fun SalviaLogo(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 76.dp,
    contentDescription: String = "Salvia-H.Ai logo"
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFF37206B), Color(0xFF151322), Color(0xFF0B0C10))
                )
            )
            .border(
                BorderStroke(
                    1.5.dp,
                    Brush.linearGradient(listOf(SalviaGold, SalviaMagenta, IceCyan))
                ),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.app_launcher_frozen_mascot),
            contentDescription = contentDescription,
            modifier = Modifier
                .size(size * 0.78f)
                .clip(CircleShape)
        )
    }
}

@Composable
fun SalviaBrandLockup(
    modifier: Modifier = Modifier,
    showByline: Boolean = true,
    strings: AppStrings
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SalviaLogo(size = 42.dp)
        Spacer(modifier = Modifier.width(11.dp))
        Column {
            Text(
                text = strings.appName,
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp
            )
            if (showByline) {
                Text(
                    text = strings.brandByline,
                    color = TextSecondary,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }
    }
}

fun salviaGradient(): Brush = Brush.linearGradient(listOf(VioletPrimary, SalviaMagenta, IceCyan))
