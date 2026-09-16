package com.example.ui.screens.chat.components

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.AiModel
import com.example.domain.model.ModelCapability
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletLight
import com.example.ui.theme.VioletPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectorSheet(
    sheetState: SheetState,
    selectedModelId: String,
    availableModels: List<AiModel>,
    onSelectModel: (String) -> Unit,
    onAddCustomModel: (String) -> Unit,
    onDeleteCustomModel: (String) -> Unit,
    onToggleFavorite: (AiModel) -> Unit,
    onDismiss: () -> Unit
) {
    var customModelInput by remember { mutableStateOf("") }
    var showAddCustomDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkSurface,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .testTag("model_selector_sheet")
        ) {
            Text(
                text = "Select HCNSEC Model",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(14.dp))

            // AUTO ROUTER OPTION
            val isAutoSelected = selectedModelId == "auto"
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(
                        width = if (isAutoSelected) 2.dp else 1.dp,
                        color = if (isAutoSelected) VioletPrimary else DarkBorder,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable {
                        onSelectModel("auto")
                        onDismiss()
                    }
                    .testTag("select_model_auto"),
                color = if (isAutoSelected) Color(0xFF20133A) else DarkSurfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(VioletPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Auto",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto Mode (Recommended)",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Classifies task and selects optimal available HCNSEC model",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    if (isAutoSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = VioletPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Available Models",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = "${availableModels.size} models",
                    color = VioletLight,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Models List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .height(280.dp)
            ) {
                items(availableModels, key = { it.id }) { model ->
                    val isSelected = selectedModelId == model.id
                    ModelItemRow(
                        model = model,
                        isSelected = isSelected,
                        onSelect = {
                            onSelectModel(model.id)
                            onDismiss()
                        },
                        onToggleFavorite = { onToggleFavorite(model) },
                        onDelete = { onDeleteCustomModel(model.id) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Custom Model Entry Form
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, DarkBorder, RoundedCornerShape(14.dp)),
                color = DarkBg
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Custom Model ID",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customModelInput,
                            onValueChange = { customModelInput = it },
                            placeholder = {
                                Text(text = "e.g. qwen-max, deepseek-r1", color = Color(0xFF64748B), fontSize = 13.sp)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("custom_model_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = VioletPrimary,
                                unfocusedBorderColor = DarkBorder,
                                focusedContainerColor = DarkSurface,
                                unfocusedContainerColor = DarkSurface,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (customModelInput.isNotBlank()) {
                                    onAddCustomModel(customModelInput.trim())
                                    customModelInput = ""
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("save_custom_model_button")
                        ) {
                            Text(text = "Save", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ModelItemRow(
    model: AiModel,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) VioletPrimary else DarkBorder,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onSelect() }
            .testTag("model_item_${model.id}"),
        color = if (isSelected) Color(0xFF1E1633) else DarkSurfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = model.displayName,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )

                    if (model.isCustom) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF332050))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(text = "Custom", color = VioletLight, fontSize = 10.sp)
                        }
                    }
                }

                // Capability Badges
                if (model.capabilities.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (model.capabilities.contains(ModelCapability.REASONING)) {
                            CapabilityBadge("Reasoning", Color(0xFF10B981))
                        }
                        if (model.capabilities.contains(ModelCapability.CODING)) {
                            CapabilityBadge("Coding", Color(0xFF3B82F6))
                        }
                        if (model.capabilities.contains(ModelCapability.VISION)) {
                            CapabilityBadge("Vision", Color(0xFFEC4899))
                        }
                        if (model.capabilities.contains(ModelCapability.FAST_CHAT)) {
                            CapabilityBadge("Fast", Color(0xFFF59E0B))
                        }
                    }
                }
            }

            // Favorite button
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (model.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Favorite",
                    tint = if (model.isFavorite) Color(0xFFFBBF24) else TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (model.isCustom) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = VioletPrimary,
                    modifier = Modifier
                        .size(18.dp)
                        .padding(start = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun CapabilityBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .border(0.5.dp, color.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(text = text, color = color, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
    }
}
