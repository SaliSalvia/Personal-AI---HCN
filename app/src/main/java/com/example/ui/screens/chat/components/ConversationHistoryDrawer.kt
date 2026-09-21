package com.example.ui.screens.chat.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.WorkspaceEntity
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkSurfaceVariant
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
import com.example.ui.localization.LocalAppStrings
import com.example.ui.localization.SalviaBrandLockup

@Composable
fun ConversationHistoryDrawer(
    conversations: List<ConversationEntity>,
    workspaces: List<WorkspaceEntity>,
    currentConversationId: String?,
    onSelectConversation: (String) -> Unit,
    onSelectWorkspace: (String) -> Unit,
    onNewChat: () -> Unit,
    onRenameConversation: (String, String) -> Unit,
    onTogglePin: (String, Boolean) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onCloseDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    var searchQuery by remember { mutableStateOf("") }
    var renameTargetId by remember { mutableStateOf<String?>(null) }
    var renameTitleInput by remember { mutableStateOf("") }

    val filtered = conversations.filter {
        searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true)
    }
    val pinned = filtered.filter { it.isPinned }
    val recent = filtered.filter { !it.isPinned }

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(310.dp)
            .border(BorderStroke(1.dp, Brush.verticalGradient(listOf(IceFrostBorder.copy(alpha = 0.4f), Color(0x1538BDF8), IceVioletBorder.copy(alpha = 0.3f))))),
        color = IceGlassBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 14.dp, vertical = 18.dp)
        ) {
            // App Title & New Chat Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SalviaBrandLockup(strings = strings, showByline = false)

                val newChatBrush = Brush.linearGradient(
                    listOf(VioletPrimary, Color(0xFF6366F1), IceCyan)
                )
                Button(
                    onClick = {
                        onNewChat()
                        onCloseDrawer()
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(newChatBrush)
                        .border(BorderStroke(1.dp, IceFrostBorderActive), RoundedCornerShape(14.dp))
                        .testTag("new_chat_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New chat",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = strings.newChat, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Search Bar & Filter UI
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(text = strings.searchConversations, color = Color(0xFF64748B), fontSize = 12.sp)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = if (searchQuery.isNotBlank()) IceCyan else TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("history_search_input"),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = IceCyan,
                    unfocusedBorderColor = Color(0x2838BDF8),
                    focusedContainerColor = Color(0x66080A12),
                    unfocusedContainerColor = Color(0x44080A12),
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )

            // Search Active Indicator & Match Count
            if (searchQuery.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Filtered by \"$searchQuery\"",
                        color = IceCyanLight,
                        fontSize = 11.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${filtered.size} found",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Conversations & Workspaces List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // WORKSPACES SECTION
                if (workspaces.isNotEmpty()) {
                    item {
                        Text(
                            text = strings.workspaces,
                            color = Color(0xFFFBBF24),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }

                    items(workspaces, key = { it.id }) { ws ->
                        WorkspaceDrawerItem(
                            workspace = ws,
                            onClick = {
                                onSelectWorkspace(ws.id)
                                onCloseDrawer()
                            }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    item { Spacer(modifier = Modifier.height(10.dp)) }
                }

                // PINNED SECTION
                if (pinned.isNotEmpty()) {
                    item {
                        Text(
                            text = strings.pinned,
                            color = VioletLight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }

                    items(pinned, key = { it.id }) { conv ->
                        ConversationDrawerItem(
                            conversation = conv,
                            searchQuery = searchQuery,
                            isSelected = conv.id == currentConversationId,
                            onClick = {
                                onSelectConversation(conv.id)
                                onCloseDrawer()
                            },
                            onRename = {
                                renameTargetId = conv.id
                                renameTitleInput = conv.title
                            },
                            onTogglePin = { onTogglePin(conv.id, conv.isPinned) },
                            onDelete = { onDeleteConversation(conv.id) }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    item { Spacer(modifier = Modifier.height(10.dp)) }
                }

                // RECENT CONVERSATIONS
                item {
                    Text(
                        text = if (searchQuery.isNotBlank()) strings.matchingConversations else strings.recentConversations,
                        color = if (searchQuery.isNotBlank()) IceCyanLight else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }

                if (recent.isEmpty() && pinned.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = Color(0xFF475569),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (searchQuery.isNotBlank()) "No conversations match \"$searchQuery\"" else "No conversations yet",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    items(recent, key = { it.id }) { conv ->
                        ConversationDrawerItem(
                            conversation = conv,
                            searchQuery = searchQuery,
                            isSelected = conv.id == currentConversationId,
                            onClick = {
                                onSelectConversation(conv.id)
                                onCloseDrawer()
                            },
                            onRename = {
                                renameTargetId = conv.id
                                renameTitleInput = conv.title
                            },
                            onTogglePin = { onTogglePin(conv.id, conv.isPinned) },
                            onDelete = { onDeleteConversation(conv.id) }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Drawer Bottom: Settings
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                    .clickable {
                        onOpenSettings()
                        onCloseDrawer()
                    }
                    .testTag("open_settings_button"),
                color = DarkBg
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = strings.settingsApiKeys,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    // Rename Dialog
    if (renameTargetId != null) {
        AlertDialog(
            onDismissRequest = { renameTargetId = null },
            title = { Text(text = "Rename Conversation", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = renameTitleInput,
                    onValueChange = { renameTitleInput = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = VioletPrimary,
                        unfocusedBorderColor = DarkBorder
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = renameTargetId ?: return@Button
                        if (renameTitleInput.isNotBlank()) {
                            onRenameConversation(id, renameTitleInput.trim())
                        }
                        renameTargetId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTargetId = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }
}

@Composable
private fun ConversationDrawerItem(
    conversation: ConversationEntity,
    searchQuery: String = "",
    isSelected: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val itemShape = RoundedCornerShape(14.dp)
    val itemBorder = if (isSelected) {
        BorderStroke(1.dp, IceFrostBorderActive)
    } else {
        BorderStroke(1.dp, Color(0x1F38BDF8))
    }
    val itemBg = if (isSelected) Color(0x3838BDF8) else Color(0x33121526)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(itemShape)
            .border(itemBorder, itemShape)
            .clickable { onClick() }
            .testTag("conversation_row_${conversation.id}"),
        color = itemBg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (conversation.isPinned) Icons.Default.PushPin else Icons.AutoMirrored.Filled.Chat,
                contentDescription = "Chat",
                tint = if (conversation.isPinned) VioletLight else TextSecondary,
                modifier = Modifier.size(15.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Highlight matched search terms if query is present
            if (searchQuery.isNotBlank() && conversation.title.contains(searchQuery, ignoreCase = true)) {
                val title = conversation.title
                val queryLower = searchQuery.lowercase()
                val titleLower = title.lowercase()
                val annotatedTitle = buildAnnotatedString {
                    var currentIndex = 0
                    while (currentIndex < title.length) {
                        val matchIndex = titleLower.indexOf(queryLower, currentIndex)
                        if (matchIndex == -1) {
                            append(title.substring(currentIndex))
                            break
                        }
                        if (matchIndex > currentIndex) {
                            append(title.substring(currentIndex, matchIndex))
                        }
                        withStyle(
                            style = SpanStyle(
                                color = IceCyan,
                                fontWeight = FontWeight.ExtraBold,
                                background = Color(0x3338BDF8)
                            )
                        ) {
                            append(title.substring(matchIndex, matchIndex + searchQuery.length))
                        }
                        currentIndex = matchIndex + searchQuery.length
                    }
                }
                Text(
                    text = annotatedTitle,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Text(
                    text = conversation.title,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.background(DarkSurfaceElevated)
                ) {
                    DropdownMenuItem(
                        text = { Text(if (conversation.isPinned) "Unpin" else "Pin", color = TextPrimary, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null, tint = VioletLight, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            menuExpanded = false
                            onTogglePin()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename", color = TextPrimary, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = ErrorRed, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkspaceDrawerItem(
    workspace: WorkspaceEntity,
    onClick: () -> Unit
) {
    val wsShape = RoundedCornerShape(14.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(wsShape)
            .border(BorderStroke(1.dp, Color(0x4DFBBF24)), wsShape)
            .clickable { onClick() }
            .testTag("workspace_row_${workspace.id}"),
        color = Color(0x33261D12)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.FolderZip,
                contentDescription = "Workspace",
                tint = Color(0xFFFBBF24),
                modifier = Modifier.size(16.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = workspace.name,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    text = "${workspace.projectType} • ${workspace.fileCount} files",
                    color = Color(0xFFD97706),
                    fontSize = 10.sp
                )
            }
        }
    }
}
