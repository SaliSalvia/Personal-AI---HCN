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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import com.example.data.api.AiProvider
import com.example.domain.model.AiModel
import com.example.domain.model.ModelCapability
import com.example.ui.localization.LocalAppStrings
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletLight
import com.example.ui.theme.VioletPrimary

/** A provider and the models it exposes to the agent. */
private data class ProviderGroup(
    val id: String,
    val displayName: String,
    val models: List<AiModel>
)

private sealed interface SelectorRow {
    val key: String
    data class Header(val group: ProviderGroup) : SelectorRow {
        override val key: String get() = "header_${group.id}"
    }
    data class ModelRow(val model: AiModel) : SelectorRow {
        override val key: String get() = "model_${model.selectionKey}"
    }
}

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
    isRefreshing: Boolean,
    refreshError: String?,
    onRefreshModels: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalAppStrings.current
    var customModelInput by remember { mutableStateOf("") }

    // Models are grouped by their owning provider, so every configured API becomes a
    // collapsible branch with its own models as sub-items.
    val groups = remember(availableModels) {
        availableModels
            .groupBy { it.providerId.ifBlank { "other" } }
            .map { (providerId, models) ->
                ProviderGroup(
                    id = providerId,
                    displayName = if (providerId == "other") "Other" else AiProvider.displayNameOf(providerId),
                    models = models.sortedBy { it.displayName.lowercase() }
                )
            }
            .sortedBy { it.displayName.lowercase() }
    }

    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(groups) {
        if (groups.isEmpty() || expanded.isNotEmpty()) return@LaunchedEffect
        val selectedGroup = groups.firstOrNull { group ->
            group.models.any { it.selectionKey == selectedModelId }
        } ?: groups.first()
        groups.forEach { group -> expanded[group.id] = group.id == selectedGroup.id }
    }

    val expandedSnapshot = expanded.toMap()
    val rows = remember(groups, expandedSnapshot) {
        buildList {
            groups.forEach { group ->
                add(SelectorRow.Header(group))
                if (expandedSnapshot[group.id] == true) {
                    group.models.forEach { add(SelectorRow.ModelRow(it)) }
                }
            }
        }
    }

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
                text = strings.modelSelectorTitle,
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
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.agentAutoTitle,
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = strings.agentAutoSubtitle,
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    if (isAutoSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
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
                    text = strings.allProvidersModelsLabel,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isRefreshing) "…" else strings.modelCount(availableModels.size),
                        color = VioletLight,
                        fontSize = 12.sp
                    )
                    IconButton(
                        onClick = onRefreshModels,
                        enabled = !isRefreshing,
                        modifier = Modifier.size(32.dp).testTag("refresh_all_models")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = strings.refreshModelsLabel,
                            tint = VioletLight,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            if (refreshError != null) {
                Text(
                    text = refreshError,
                    color = Color(0xFFFCA5A5),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Provider -> model hierarchical list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
            ) {
                items(rows, key = { it.key }) { row ->
                    when (row) {
                        is SelectorRow.Header -> {
                            ProviderHeaderRow(
                                group = row.group,
                                isExpanded = expandedSnapshot[row.group.id] == true,
                                onToggle = { expanded[row.group.id] = expandedSnapshot[row.group.id] != true }
                            )
                        }
                        is SelectorRow.ModelRow -> {
                            ModelItemRow(
                                model = row.model,
                                isSelected = selectedModelId == row.model.selectionKey,
                                onSelect = {
                                    onSelectModel(row.model.selectionKey)
                                    onDismiss()
                                },
                                onToggleFavorite = { onToggleFavorite(row.model) },
                                onDelete = { onDeleteCustomModel(row.model.id) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Custom Model Entry Form (id added without a catalogue entry)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, DarkBorder, RoundedCornerShape(14.dp)),
                color = DarkBg
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = strings.customModelIdLabel,
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
                                Text(text = strings.customModelHint, color = Color(0xFF64748B), fontSize = 13.sp)
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
                            Text(text = strings.saveAction, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProviderHeaderRow(
    group: ProviderGroup,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
            .clickable { onToggle() }
            .testTag("provider_group_${group.id}"),
        color = DarkSurfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(VioletPrimary)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = group.displayName,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = group.models.size.toString(),
                color = VioletLight,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
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
            .padding(start = 14.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) VioletPrimary else DarkBorder,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onSelect() }
            .testTag("model_item_${model.selectionKey}"),
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
                    contentDescription = null,
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
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = VioletPrimary,
                    modifier = Modifier.size(18.dp)
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
