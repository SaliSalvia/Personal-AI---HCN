package com.example.ui.screens.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.screens.chat.components.AgentTraceView
import com.example.ui.screens.chat.components.AttachmentTray
import com.example.ui.screens.chat.components.ChatInputBar
import com.example.ui.screens.chat.components.ConversationHistoryDrawer
import com.example.ui.screens.chat.components.MessageItem
import com.example.ui.screens.chat.components.ModelSelectorSheet
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateToWorkspace: (String) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val listState = rememberLazyListState()

    val currentConversationId by viewModel.currentConversationId.collectAsState()
    val activeWorkspaceId by viewModel.activeWorkspaceId.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val streamingMessage by viewModel.streamingMessage.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val selectedModelId by viewModel.selectedModelId.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val attachments by viewModel.attachments.collectAsState()
    val currentTrace by viewModel.currentTrace.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val conversations by viewModel.conversations.collectAsState()
    val workspaces by viewModel.workspaces.collectAsState()

    var showModelSheet by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // File pickers
    val genericFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.attachFileFromUri(context, it) }
    }

    val zipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.attachFileFromUri(context, it) }
    }

    // Auto-scroll smoothly when new messages arrive or stream updates
    LaunchedEffect(messages.size) {
        val totalCount = messages.size + (if (streamingMessage != null) 1 else 0)
        if (totalCount > 0) {
            listState.animateScrollToItem(totalCount - 1)
        }
    }

    LaunchedEffect(streamingMessage?.reasoningContent?.length, streamingMessage?.content?.length) {
        if (streamingMessage != null) {
            val totalCount = messages.size + 1
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            if (lastVisible >= totalCount - 2) {
                listState.scrollToItem(totalCount - 1)
            }
        }
    }

    val currentTitle = conversations.find { it.id == currentConversationId }?.title ?: "SALi-HCNSEC"
    val activeWorkspace = workspaces.find { it.id == activeWorkspaceId }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ConversationHistoryDrawer(
                conversations = conversations,
                workspaces = workspaces,
                currentConversationId = currentConversationId,
                onSelectConversation = { convId ->
                    viewModel.loadConversation(convId)
                },
                onSelectWorkspace = { wsId ->
                    onNavigateToWorkspace(wsId)
                },
                onNewChat = {
                    viewModel.startNewConversation()
                },
                onRenameConversation = { id, title ->
                    viewModel.renameConversation(id, title)
                },
                onTogglePin = { id, pin ->
                    viewModel.togglePinConversation(id, pin)
                },
                onDeleteConversation = { id ->
                    viewModel.deleteConversation(id)
                },
                onOpenSettings = onNavigateToSettings,
                onCloseDrawer = {
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = currentTitle,
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            if (activeWorkspace != null) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF2E2211))
                                        .clickable { onNavigateToWorkspace(activeWorkspace.id) }
                                        .padding(horizontal = 6.dp, vertical = 1.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderZip,
                                        contentDescription = "Workspace",
                                        tint = Color(0xFFFBBF24),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${activeWorkspace.name} (${activeWorkspace.projectType})",
                                        color = Color(0xFFFDE68A),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier.testTag("drawer_toggle_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open Drawer",
                                tint = TextPrimary
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.startNewConversation() },
                            modifier = Modifier.testTag("appbar_new_chat_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "New Chat",
                                tint = VioletLight
                            )
                        }

                        Box {
                            IconButton(
                                onClick = { showOptionsMenu = true },
                                modifier = Modifier.testTag("chat_more_options_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More Options",
                                    tint = TextSecondary
                                )
                            }

                            DropdownMenu(
                                expanded = showOptionsMenu,
                                onDismissRequest = { showOptionsMenu = false },
                                modifier = Modifier.background(DarkSurface)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Export as Markdown", color = TextPrimary, fontSize = 13.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Share, contentDescription = null, tint = VioletLight, modifier = Modifier.size(16.dp))
                                    },
                                    onClick = {
                                        showOptionsMenu = false
                                        val markdown = viewModel.exportConversationAsMarkdown()
                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("SALi Chat Export", markdown)
                                        clipboard.setPrimaryClip(clip)
                                        android.widget.Toast.makeText(context, "Exported chat to clipboard as Markdown", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Regenerate Response", color = TextPrimary, fontSize = 13.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Refresh, contentDescription = null, tint = VioletLight, modifier = Modifier.size(16.dp))
                                    },
                                    onClick = {
                                        showOptionsMenu = false
                                        viewModel.regenerateLastResponse()
                                    },
                                    enabled = messages.isNotEmpty() && !isGenerating
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = IceGlassBg
                    ),
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
                // Error banner
                AnimatedVisibility(
                    visible = errorMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    if (errorMessage != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0x33EF4444))
                                .border(1.dp, Color(0x66EF4444))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "Error",
                                tint = ErrorRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = errorMessage!!,
                                color = Color(0xFFFCA5A5),
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Chat Messages Content Area
                Box(modifier = Modifier.weight(1f)) {
                    if (messages.isEmpty() && streamingMessage == null) {
                        // Empty State View
                        EmptyChatState(
                            onSelectPrompt = { prompt ->
                                viewModel.onInputChanged(prompt)
                            },
                            onUploadZip = { zipLauncher.launch("application/zip") }
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp)
                                .testTag("messages_list")
                        ) {
                            items(messages, key = { it.id }) { msg ->
                                MessageItem(message = msg)
                            }

                            if (streamingMessage != null) {
                                item(key = "streaming_message") {
                                    MessageItem(message = streamingMessage!!)
                                }
                            }

                            // Observable Agent Trace
                            if (currentTrace.isNotEmpty()) {
                                item(key = "agent_trace") {
                                    AgentTraceView(
                                        steps = currentTrace,
                                        isGenerating = isGenerating,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            }

                            item { Spacer(modifier = Modifier.height(12.dp)) }
                        }
                    }
                }

                // Attached items tray
                AttachmentTray(
                    attachments = attachments,
                    onRemoveAttachment = { id -> viewModel.removeAttachment(id) }
                )

                // Input Bar
                ChatInputBar(
                    inputText = inputText,
                    onInputChanged = { viewModel.onInputChanged(it) },
                    isGenerating = isGenerating,
                    selectedModelDisplay = if (selectedModelId == "auto") "Auto (Optimal)" else selectedModelId,
                    onOpenModelSelector = { showModelSheet = true },
                    onAttachFile = { genericFileLauncher.launch("*/*") },
                    onAttachZip = { zipLauncher.launch("application/zip") },
                    onSend = { viewModel.sendMessage() },
                    onStop = { viewModel.stopGeneration() },
                    hasAttachments = attachments.isNotEmpty()
                )
            }
        }
    }

    // Model Selector Bottom Sheet
    if (showModelSheet) {
        ModelSelectorSheet(
            sheetState = sheetState,
            selectedModelId = selectedModelId,
            availableModels = availableModels,
            onSelectModel = { modelId -> viewModel.selectModel(modelId) },
            onAddCustomModel = { modelId -> viewModel.addCustomModel(modelId) },
            onDeleteCustomModel = { modelId -> viewModel.deleteCustomModel(modelId) },
            onToggleFavorite = { model -> viewModel.toggleFavoriteModel(model) },
            onDismiss = { showModelSheet = false }
        )
    }
}

@Composable
private fun EmptyChatState(
    onSelectPrompt: (String) -> Unit,
    onUploadZip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Color(0xFF14172B))
                .border(2.dp, Brush.linearGradient(listOf(IceCyan, VioletPrimary, IceFrostBorder)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.sali_logo_icon),
                contentDescription = "SALi-HCNSEC",
                modifier = Modifier.size(58.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "SALi-HCNSEC",
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Production-grade AI agent powered exclusively by HCNSEC",
            color = IceCyanLight.copy(alpha = 0.8f),
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Prompt suggestions
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PromptCard(
                title = "Upload & Analyze Project ZIP",
                subtitle = "Extract, build tree, and inspect architecture",
                onClick = onUploadZip,
                accentColor = Color(0xFFF59E0B)
            )

            PromptCard(
                title = "Deep Code Refactoring",
                subtitle = "Generate idiomatic Kotlin coroutines & architecture",
                onClick = { onSelectPrompt("Review my Kotlin code structure and suggest idiomatic architectural refactorings.") },
                accentColor = IceCyan
            )

            PromptCard(
                title = "Deep Logical & Math Reasoning",
                subtitle = "Solve complex logic step-by-step with HCNSEC models",
                onClick = { onSelectPrompt("Provide a step-by-step proof and deep logical derivation for: ") },
                accentColor = Color(0xFF10B981)
            )
        }
    }
}

@Composable
private fun PromptCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    accentColor: Color
) {
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
            )
            .clickable { onClick() },
        color = IceGlassBg
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(accentColor)
                    .border(1.dp, accentColor.copy(alpha = 0.5f), CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}
