package com.example.ui.screens.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.AttachmentItem
import com.example.domain.model.AttachmentStatus
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletLight
import com.example.ui.theme.VioletPrimary

@Composable
fun AttachmentTray(
    attachments: List<AttachmentItem>,
    onRemoveAttachment: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (attachments.isEmpty()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        attachments.forEach { item ->
            AttachmentChip(
                item = item,
                onRemove = { onRemoveAttachment(item.id) }
            )
        }
    }
}

@Composable
private fun AttachmentChip(
    item: AttachmentItem,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
            .testTag("attachment_chip_${item.id}"),
        color = DarkSurfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = when {
                item.isZipWorkspace || item.name.endsWith(".zip", ignoreCase = true) -> Icons.Default.FolderZip
                item.mimeType.startsWith("image/") -> Icons.Default.Image
                item.name.endsWith(".pdf", ignoreCase = true) -> Icons.Default.Description
                else -> Icons.AutoMirrored.Filled.InsertDriveFile
            }

            Icon(
                imageVector = icon,
                contentDescription = item.name,
                tint = if (item.isZipWorkspace) Color(0xFFF59E0B) else VioletLight,
                modifier = Modifier.size(18.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = item.name,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )

            Spacer(modifier = Modifier.width(4.dp))

            Text(
                text = formatFileSize(item.sizeBytes),
                color = TextSecondary,
                fontSize = 10.sp
            )

            if (item.status == AttachmentStatus.EXTRACTING || item.status == AttachmentStatus.INDEXING) {
                Spacer(modifier = Modifier.width(6.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    color = VioletPrimary,
                    strokeWidth = 1.5.dp
                )
            }

            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove attachment",
                    tint = TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> String.format("%.1f MB", mb)
        kb >= 1.0 -> String.format("%.1f KB", kb)
        else -> "$bytes B"
    }
}
