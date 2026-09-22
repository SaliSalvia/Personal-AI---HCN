package com.example.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.AutoAwesome
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
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
    val availableModels by viewModel.availableModels.collectAsState()
    val language by languageRepository.language.collectAsState()
    val strings = LocalAppStrings.current

    var showChangeKeyDialog by remember { mutableStateOf(false) }
    var showRemoveKeyDialog by remember { mutableStateOf(false) }
    var newKeyInput by remember { mutableStateOf("") }
    var changeKeyError by remember { mutableStateOf<String?>(null) }
    var isUpdatingKey by remember { mutableStateOf(false) }
    val activeProvider by viewModel.activeProvider.collectAsState()
    var providerDialog by remember { mutableStateOf<AiProvider?>(null) }
    var providerKeyInput by remember { mutableStateOf("") }
    var customBaseUrlInput by remember { mutableStateOf("") }
    var customModelInput by remember { mutableStateOf("") }
    var providerError by remember { mutableStateOf<String?>(null) }
    var isSavingProvider by remember { mutableStateOf(false) }

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

            // Language is intentionally first: switching it immediately updates every screen.
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

            // SECTION 1: HCNSEC API KEY CREDENTIALS
            Text(
                text = "HCNSEC CREDENTIALS",
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
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF261942)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = VioletLight,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Current API Key",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = viewModel.maskedApiKey,
                                color = TextSecondary,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Test connection status feedback
                    when (testState) {
                        is ConnectionTestState.Testing -> {
                            Row(
                                modifier = Modifier.padding(bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = VioletPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Pinging https://api.hcnsec.cn/v1...", color = VioletLight, fontSize = 12.sp)
                            }
                        }
                        is ConnectionTestState.Success -> {
                            val count = (testState as ConnectionTestState.Success).modelCount
                            Row(
                                modifier = Modifier
                                    .padding(bottom = 12.dp)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x2210B981))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Online: verified $count models from HCNSEC.", color = Color(0xFFA7F3D0), fontSize = 12.sp)
                            }
                        }
                        is ConnectionTestState.Error -> {
                            val msg = (testState as ConnectionTestState.Error).message
                            Row(
                                modifier = Modifier
                                    .padding(bottom = 12.dp)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x22EF4444))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(msg, color = Color(0xFFFCA5A5), fontSize = 12.sp)
                            }
                        }
                        else -> {}
                    }

                    // Key Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.testConnection() },
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .testTag("test_connection_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Test", fontSize = 12.sp, color = TextPrimary)
                        }

                        Button(
                            onClick = { showChangeKeyDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .testTag("change_key_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Update", fontSize = 12.sp, color = Color.White)
                        }

                        Button(
                            onClick = { showRemoveKeyDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .testTag("remove_key_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF381515)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Remove", fontSize = 12.sp, color = ErrorRed)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // SECTION 2: ADDITIONAL AI PROVIDERS
            Text(
                text = "ADDITIONAL AI PROVIDERS",
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
                    Text(
                        "Paste a provider key below. Salvia detects Google AI Studio (AIza…), Groq (gsk_…) and OpenRouter (sk-or-…) automatically, validates it, and stores it encrypted.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    AiProvider.values().filter { it != AiProvider.HCNSEC }.forEach { provider ->
                        val configured = viewModel.providerKeyStatus(provider) != "No key configured"
                        Button(
                            onClick = {
                                if (configured) {
                                    viewModel.selectProvider(provider)
                                } else {
                                    providerDialog = provider
                                    providerKeyInput = ""
                                    customBaseUrlInput = ""
                                    customModelInput = ""
                                    providerError = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (activeProvider == provider) VioletPrimary else DarkSurfaceVariant
                            ),
                            shape = RoundedCornerShape(9.dp)
                        ) {
                            Text(
                                "${provider.displayName}${if (configured) "  •  Configured" else "  •  Add key"}",
                                color = TextPrimary,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // SECTION 3: INTELLIGENT ROUTING & PREFERENCES
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
                                "Intelligently classifies tasks (coding, math proof, creative, workspace) to optimal HCNSEC model",
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
                        text = if (defaultModel == "auto") "Auto Routing" else defaultModel,
                        color = VioletLight,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // SECTION 3: ACCOUNT USAGE / BILLING
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
                                text = "Official usage balance endpoint is not published by the current HCNSEC server configuration. Token counts are recorded during active sessions.",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // SECTION 4: SECURITY & PLATFORM GUARANTEE
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
                        Text("HCNSEC-First & Hardware-Backed Keystore", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• HCNSEC is the default provider; Google AI Studio, Groq and OpenRouter are optional and only used when you add a key for them.\n• API Keys are encrypted using AES-GCM via AndroidKeyStore.\n• Requests are sent only to the provider you have activated.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    // Additional provider key dialog. Detection is performed again in the ViewModel
    // so a pasted key is never silently stored under the wrong provider.
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

    // Update API Key Dialog
    if (showChangeKeyDialog) {
        AlertDialog(
            onDismissRequest = {
                showChangeKeyDialog = false
                changeKeyError = null
            },
            title = { Text("Update HCNSEC API Key", color = TextPrimary) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newKeyInput,
                        onValueChange = { newKeyInput = it },
                        placeholder = { Text("sk-...", color = Color(0xFF64748B)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = VioletPrimary,
                            unfocusedBorderColor = DarkBorder
                        )
                    )

                    if (changeKeyError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(changeKeyError!!, color = ErrorRed, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newKeyInput.isNotBlank()) {
                            isUpdatingKey = true
                            viewModel.updateApiKey(
                                newKey = newKeyInput.trim(),
                                onSuccess = {
                                    isUpdatingKey = false
                                    showChangeKeyDialog = false
                                    newKeyInput = ""
                                },
                                onError = { err ->
                                    isUpdatingKey = false
                                    changeKeyError = err
                                }
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                    enabled = !isUpdatingKey
                ) {
                    if (isUpdatingKey) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Validate & Save")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangeKeyDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    // Remove API Key Dialog
    if (showRemoveKeyDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveKeyDialog = false },
            title = { Text("Remove API Key?", color = TextPrimary) },
            text = {
                Text(
                    "This will delete the encrypted key from Android Keystore. You will need to enter an API key again to use Salvia-H.Ai.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRemoveKeyDialog = false
                        viewModel.removeApiKey(onLoggedOut)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Remove Key")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveKeyDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }
}
