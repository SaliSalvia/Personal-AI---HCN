package com.example.ui.screens.workspace

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.domain.model.WorkspaceFileInfo
import com.example.ui.localization.LocalAppStrings
import com.example.ui.screens.chat.components.CodeBlockView
import com.example.ui.screens.chat.components.formatFileSize
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.IceCyan
import com.example.ui.theme.IceCyanLight
import com.example.ui.theme.IceFrostBorder
import com.example.ui.theme.IceGlassBg
import com.example.ui.theme.IceGlassElevated
import com.example.ui.theme.IceVioletBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletLight
import com.example.ui.theme.VioletPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    viewModel: WorkspaceViewModel,
    onNavigateBack: () -> Unit,
    onOpenInChat: (String) -> Unit
) {
    val workspace by viewModel.workspace.collectAsState()
    val fileTree by viewModel.fileTree.collectAsState()
    val selectedFilePath by viewModel.selectedFilePath.collectAsState()
    val selectedFileContent by viewModel.selectedFileContent.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val strings = LocalAppStrings.current

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val filePreviewSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = workspace?.name ?: strings.workspace,
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        workspace?.let {
                            Text(
                                text = "${it.projectType} • ${it.fileCount} files",
                                color = Color(0xFFFBBF24),
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            workspace?.id?.let { onOpenInChat(it) }
                        },
                        modifier = Modifier.testTag("open_workspace_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = "Chat with Project",
                            tint = VioletLight
                        )
                    }

                    IconButton(onClick = { showDeleteConfirmDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Workspace",
                            tint = ErrorRed
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = IceGlassBg),
                modifier = Modifier.border(
                    BorderStroke(1.dp, Brush.horizontalGradient(listOf(IceFrostBorder.copy(alpha = 0.4f), Color(0x1538BDF8), IceVioletBorder.copy(alpha = 0.3f))))
                )
            )
        },
        containerColor = DarkBg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tabs
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = IceGlassBg,
                contentColor = TextPrimary,
                indicator = { tabPositions ->
                    SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = IceCyan
                    )
                }
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text(strings.filesTree, fontSize = 14.sp) }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text(strings.projectAnalysis, fontSize = 14.sp) }
                )
            }

            when (selectedTabIndex) {
                0 -> {
                    FilesTabView(
                        fileTree = fileTree,
                        searchQuery = searchQuery,
                        onSearchChanged = { viewModel.onSearchQueryChanged(it) },
                        onSelectFile = { viewModel.selectFile(it) }
                    )
                }
                1 -> {
                    AnalysisTabView(
                        workspace = workspace,
                        onStartChat = {
                            workspace?.id?.let { onOpenInChat(it) }
                        }
                    )
                }
            }
        }
    }

    // File Preview Bottom Sheet
    if (selectedFilePath != null && selectedFileContent != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.clearSelectedFile() },
            sheetState = filePreviewSheetState,
            containerColor = DarkSurface,
            scrimColor = Color.Black.copy(alpha = 0.6f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp)
                    .testTag("file_preview_sheet")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedFilePath!!.split("/").last(),
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = selectedFilePath!!,
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    IconButton(onClick = { viewModel.clearSelectedFile() }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val ext = selectedFilePath!!.substringAfterLast('.', "text")
                CodeBlockView(
                    code = selectedFileContent!!,
                    language = ext,
                    modifier = Modifier.height(340.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        val path = selectedFilePath
                        viewModel.clearSelectedFile()
                        workspace?.id?.let { onOpenInChat(it) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Chat,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.askAboutFile, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Workspace?", color = TextPrimary) },
            text = {
                Text(
                    "This will remove the workspace folder and its indexed project files from local storage.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        viewModel.deleteWorkspace(onNavigateBack)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }
}

@Composable
private fun FilesTabView(
    fileTree: List<WorkspaceFileInfo>,
    searchQuery: String,
    onSearchChanged: (String) -> Unit,
    onSelectFile: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChanged,
            placeholder = { Text(strings.searchFiles, color = Color(0xFF64748B), fontSize = 13.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .testTag("workspace_file_search"),
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = VioletPrimary,
                unfocusedBorderColor = DarkBorder,
                focusedContainerColor = DarkSurface,
                unfocusedContainerColor = DarkSurface,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        val filteredTree = remember(fileTree, searchQuery) {
            if (searchQuery.isBlank()) fileTree
            else filterTree(fileTree, searchQuery.trim().lowercase())
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(filteredTree, key = { it.relativePath }) { item ->
                FileTreeNodeItem(
                    item = item,
                    depth = 0,
                    onSelectFile = onSelectFile
                )
            }
        }
    }
}

private fun filterTree(nodes: List<WorkspaceFileInfo>, query: String): List<WorkspaceFileInfo> {
    val result = mutableListOf<WorkspaceFileInfo>()
    for (node in nodes) {
        if (node.isDirectory) {
            val matchingChildren = filterTree(node.children, query)
            if (matchingChildren.isNotEmpty() || node.name.lowercase().contains(query)) {
                result.add(node.copy(children = matchingChildren))
            }
        } else {
            if (node.name.lowercase().contains(query) || node.relativePath.lowercase().contains(query)) {
                result.add(node)
            }
        }
    }
    return result
}

@Composable
private fun FileTreeNodeItem(
    item: WorkspaceFileInfo,
    depth: Int,
    onSelectFile: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(depth < 1) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (item.isDirectory) {
                        isExpanded = !isExpanded
                    } else {
                        onSelectFile(item.relativePath)
                    }
                }
                .padding(start = (depth * 18).dp, top = 6.dp, bottom = 6.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when {
                    item.isDirectory && isExpanded -> Icons.Default.FolderOpen
                    item.isDirectory -> Icons.Default.Folder
                    item.isImportantProjectFile -> Icons.Default.Description
                    else -> Icons.AutoMirrored.Filled.InsertDriveFile
                },
                contentDescription = null,
                tint = when {
                    item.isDirectory -> Color(0xFFFBBF24)
                    item.isImportantProjectFile -> VioletLight
                    else -> TextSecondary
                },
                modifier = Modifier.size(16.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = item.name,
                color = if (item.isImportantProjectFile) VioletLight else TextPrimary,
                fontSize = 13.sp,
                fontWeight = if (item.isImportantProjectFile) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )

            if (!item.isDirectory) {
                Text(
                    text = formatFileSize(item.sizeBytes),
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        if (item.isDirectory && isExpanded) {
            item.children.forEach { child ->
                FileTreeNodeItem(
                    item = child,
                    depth = depth + 1,
                    onSelectFile = onSelectFile
                )
            }
        }
    }
}

@Composable
private fun AnalysisTabView(
    workspace: com.example.data.local.entity.WorkspaceEntity?,
    onStartChat: () -> Unit
) {
    if (workspace == null) return

    val strings = LocalAppStrings.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Project Overview Card with Frosted Icy Corners
        val cardShape = RoundedCornerShape(18.dp)
        Surface(
            modifier = Modifier
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
                ),
            shape = cardShape,
            color = IceGlassBg
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = strings.projectArchitecture,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatBox(title = "Type", value = workspace.projectType)
                    StatBox(title = "Files", value = workspace.fileCount.toString())
                    StatBox(title = "Size", value = formatFileSize(workspace.totalSizeBytes))
                }

                if (!workspace.summary.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = strings.structureOverview,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = workspace.summary,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onStartChat,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("start_workspace_chat_button"),
            colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Chat,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(strings.chatWithWorkspace, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StatBox(title: String, value: String) {
    val statShape = RoundedCornerShape(12.dp)
    Surface(
        modifier = Modifier
            .clip(statShape)
            .border(BorderStroke(1.dp, Color(0x2E38BDF8)), statShape),
        color = Color(0x55171C2E)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(text = title, color = IceCyanLight.copy(alpha = 0.8f), fontSize = 10.sp)
            Text(text = value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}
