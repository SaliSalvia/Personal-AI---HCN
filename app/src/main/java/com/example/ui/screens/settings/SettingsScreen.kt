package com.example.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.api.AiProvider
import com.example.data.settings.AppLanguage
import com.example.data.settings.LanguageRepository
import com.example.ui.localization.LocalAppStrings
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletLight
import com.example.ui.theme.VioletPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    languageRepository: LanguageRepository,
    onNavigateBack: () -> Unit,
    onLoggedOut: () -> Unit
) {
    val isAutoRouting by viewModel.isAutoRouting.collectAsState()
    val defaultModel by viewModel.defaultModel.collectAsState()
    val testState by viewModel.testState.collectAsState()
    val accountUsage by viewModel.accountUsage.collectAsState()
    val configuredProviders by viewModel.configuredProviders.collectAsState()
    val language by languageRepository.language.collectAsState()
    val strings = LocalAppStrings.current

    var providerDialog by remember { mutableStateOf<AiProvider?>(null) }
    var providerKeyInput by remember { mutableStateOf("") }
    var customBaseUrlInput by remember { mutableStateOf("") }
    var customModelInput by remember { mutableStateOf("") }
    var providerError by remember { mutableStateOf<String?>(null) }
    var isSavingProvider by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<AiProvider?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = strings.settingsApiKeys,
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        containerColor = DarkBg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Language
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DarkBorder, RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                color = DarkSurface
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(strings.languageLabel, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(AppLanguage.ENGLISH to strings.english, AppLanguage.PERSIAN to strings.persian).forEach { (option, label) ->
                            Button(
                                onClick = { languageRepository.setLanguage(option) },
                                modifier = Modifier.weight(1f).height(38.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (language == option) VioletPrimary else DarkSurfaceVariant
                                ),
                                shape = RoundedCornerShape(9.dp)
                            ) { Text(label, color = TextPrimary, fontSize = 12.sp) }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // SECTION 1: provider credentials (several can be connected at once)
            Text(
                text = "AI PROVIDERS",
                color = VioletLight,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Connect as many providers as you want. Every configured API stays live, and all of their models become available to the agent.",
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Last connection test result
            when (val state = testState) {
                is ConnectionTestState.Testing -> StatusBanner(
                    color = VioletPrimary,
                    icon = Icons.Default.Sync,
                    text = "Testing connection…"
                )
                is ConnectionTestState.Success -> StatusBanner(
                    color = SuccessGreen,
                    icon = Icons.Default.CheckCircle,
                    text = "${state.provider.displayName}: verified ${state.modelCount} models"
                )
                is ConnectionTestState.Error -> StatusBanner(
                    color = ErrorRed,
                    icon = Icons.Default.ErrorOutline,
                    text = state.message
                )
                ConnectionTestState.Idle -> {}
            }

            AiProvider.catalog.forEach { provider ->
                val configured = provider in configuredProviders
                ProviderCard(
                    provider = provider,
                    configured = configured,
                    maskedKey = if (configured) viewModel.providerKeyStatus(provider) else null,
                    onAdd = {
                        providerDialog = provider
                        providerKeyInput = ""
                        customBaseUrlInput = ""
                        customModelInput = ""
                        providerError = null
                    },
                    onReplace = {
                        providerDialog = provider
                        providerKeyInput = ""
                        customBaseUrlInput = ""
                        customModelInput = ""
                        providerError = null
                    },
                    onTest = { viewModel.testProvider(provider) },
                    onRemove = { removeTarget = provider }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (viewModel.isProviderConfigured(AiProvider.HCNSEC)) {
                Text(
                    text = "HCNSEC ACCOUNT & USAGE",
                    color = VioletLight,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, DarkBorder, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    color = DarkSurface
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (accountUsage != null && accountUsage?.totalAvailable != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Available Balance", color = TextSecondary, fontSize = 13.sp)
                                Text(
                                    text = "${accountUsage?.totalAvailable} ${accountUsage?.currency ?: "CNY"}",
                                    color = SuccessGreen,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "The HCNSEC usage balance endpoint is not published by the current server configuration. Token counts are recorded during active sessions.",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
            }

            // SECTION 2: routing & preferences
            Text(
                text = "ROUTING & MODEL PREFERENCES",
                color = VioletLight,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DarkBorder, RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                color = DarkSurface
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto Model Routing", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Classifies each request (coding, math, vision, workspace analysis) and picks the best model across all connected providers",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = isAutoRouting,
                            onCheckedChange = { viewModel.setAutoRouting(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = VioletPrimary,
                                uncheckedTrackColor = DarkSurfaceVariant
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Default Model", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (defaultModel == "auto") "Auto Routing" else AiProvider.displayNameOf(defaultModel.substringBefore("::")),
                        color = VioletLight,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // SECTION 3: security guarantee
            Text(
                text = "SECURITY GUARANTEE",
                color = VioletLight,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DarkBorder, RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                color = DarkSurface
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = VioletPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Provider-neutral & Hardware-Backed Keystore", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• Every provider key you add is used side by side; each request goes only to the provider that owns the selected model.\n• API keys are encrypted with AES-GCM via AndroidKeyStore.\n• You can connect or remove any provider at any time.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    // Add / replace provider key dialog
    providerDialog?.let { requestedProvider ->
        AlertDialog(
            onDismissRequest = { if (!isSavingProvider) providerDialog = null },
            title = { Text("Connect ${requestedProvider.displayName}", color = TextPrimary) },
            text = {
                Column {
                    Text(requestedProvider.description, color = TextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    if (requestedProvider == AiProvider.CUSTOM) {
                        OutlinedTextField(
                            value = customBaseUrlInput,
                            onValueChange = { customBaseUrlInput = it; providerError = null },
                            placeholder = { Text("https://your-provider.example/v1", color = Color(0xFF64748B)) },
                            label = { Text("HTTPS base URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = VioletPrimary,
                                unfocusedBorderColor = DarkBorder
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = customModelInput,
                            onValueChange = { customModelInput = it },
                            placeholder = { Text("model-id (optional)", color = Color(0xFF64748B)) },
                            label = { Text("Default model") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = VioletPrimary,
                                unfocusedBorderColor = DarkBorder
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    OutlinedTextField(
                        value = providerKeyInput,
                        onValueChange = { providerKeyInput = it; providerError = null },
                        placeholder = { Text("Paste API key", color = Color(0xFF64748B)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = VioletPrimary,
                            unfocusedBorderColor = DarkBorder
                        )
                    )
                    if (providerError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(providerError!!, color = ErrorRed, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = providerKeyInput.isNotBlank() &&
                        (requestedProvider != AiProvider.CUSTOM || customBaseUrlInput.isNotBlank()) &&
                        !isSavingProvider,
                    onClick = {
                        isSavingProvider = true
                        viewModel.saveProviderKey(
                            requestedProvider,
                            providerKeyInput,
                            customBaseUrl = customBaseUrlInput,
                            customModel = customModelInput,
                            onSuccess = {
                                isSavingProvider = false
                                providerDialog = null
                                providerKeyInput = ""
                            },
                            onError = {
                                isSavingProvider = false
                                providerError = it
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary)
                ) { Text(if (isSavingProvider) "Checking…" else "Detect & Save") }
            },
            dismissButton = {
                TextButton(onClick = { providerDialog = null }) { Text("Cancel", color = TextSecondary) }
            },
            containerColor = DarkSurface
        )
    }

    // Remove provider key dialog
    removeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("Remove ${target.displayName} key?", color = TextPrimary) },
            text = {
                Text(
                    "This deletes the encrypted key for ${target.displayName}. Other connected providers stay active.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val removing = target
                        removeTarget = null
                        viewModel.removeProviderKey(removing, onAllRemoved = onLoggedOut)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) { Text("Remove key") }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) { Text("Cancel", color = TextSecondary) }
            },
            containerColor = DarkSurface
        )
    }
}

@Composable
private fun StatusBanner(color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        modifier = Modifier
            .padding(bottom = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text, color = TextPrimary, fontSize = 12.sp)
    }
}

@Composable
private fun ProviderCard(
    provider: AiProvider,
    configured: Boolean,
    maskedKey: String?,
    onAdd: () -> Unit,
    onReplace: () -> Unit,
    onTest: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (configured) 1.5.dp else 1.dp,
                color = if (configured) VioletPrimary else DarkBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .testTag("provider_row_${provider.name}"),
        shape = RoundedCornerShape(14.dp),
        color = DarkSurface
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (configured) Color(0xFF261942) else DarkSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = if (configured) VioletLight else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(provider.displayName, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(provider.description, color = TextSecondary, fontSize = 11.sp)
                }
                Text(
                    text = if (configured) "Active" else "Off",
                    color = if (configured) SuccessGreen else TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (configured && maskedKey != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = maskedKey,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (configured) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onTest,
                        modifier = Modifier.weight(1f).height(38.dp).testTag("test_key_${provider.name}"),
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Test", fontSize = 12.sp, color = TextPrimary)
                    }
                    Button(
                        onClick = onReplace,
                        modifier = Modifier.weight(1f).height(38.dp).testTag("replace_key_${provider.name}"),
                        colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Replace", fontSize = 12.sp, color = Color.White)
                    }
                    Button(
                        onClick = onRemove,
                        modifier = Modifier.weight(1f).height(38.dp).testTag("remove_key_${provider.name}"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF381515)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Remove", fontSize = 12.sp, color = ErrorRed)
                    }
                }
            } else {
                Button(
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth().height(40.dp).testTag("add_key_${provider.name}"),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add key", fontSize = 12.sp, color = TextPrimary)
                }
            }
        }
    }
}
