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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.api.hcnsec.HcnsecProviderConfig
import com.example.domain.provider.CircuitState
import com.example.domain.provider.ProviderHealthState
import com.example.domain.provider.ProviderStatus
import com.example.domain.provider.displayText
import com.example.domain.provider.isKnown
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
    onNavigateBack: () -> Unit,
    onLoggedOut: () -> Unit
) {
    val isAutoRouting by viewModel.isAutoRouting.collectAsState()
    val defaultModel by viewModel.defaultModel.collectAsState()
    val testState by viewModel.testState.collectAsState()
    val providerStatus by viewModel.providerStatus.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()

    var showChangeKeyDialog by remember { mutableStateOf(false) }
    var showRemoveKeyDialog by remember { mutableStateOf(false) }
    var newKeyInput by remember { mutableStateOf("") }
    var changeKeyError by remember { mutableStateOf<String?>(null) }
    var isUpdatingKey by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings & Credentials",
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
                                Text("Testing HCNSEC (POST /chat/completions)...", color = VioletLight, fontSize = 12.sp)
                            }
                        }
                        is ConnectionTestState.Success -> {
                            val successState = testState as ConnectionTestState.Success
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
                                Text(
                                    text = buildString {
                                        append("Online: HCNSEC responded")
                                        successState.model?.let { append(" using $it") }
                                        successState.latencyMillis?.let { append(" in $it ms") }
                                        append(".")
                                    },
                                    color = Color(0xFFA7F3D0),
                                    fontSize = 12.sp
                                )
                            }
                        }
                        is ConnectionTestState.Error -> {
                            val errorState = testState as ConnectionTestState.Error
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
                                Column {
                                    Text(errorState.message, color = Color(0xFFFCA5A5), fontSize = 12.sp)
                                    errorState.errorKind?.let { kind ->
                                        Text(
                                            text = "Category: ${kind.name.lowercase().replace('_', ' ')}",
                                            color = TextSecondary,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
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

            // SECTION 2: INTELLIGENT ROUTING & PREFERENCES
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
                    val status = providerStatus

                    if (status == null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "No provider is registered in this build.",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    } else {
                        ProviderStatusRow(label = "Provider", value = status.displayName)
                        ProviderStatusRow(
                            label = "Configuration",
                            value = if (status.configured) "Configured" else "Not configured",
                            valueColor = if (status.configured) SuccessGreen else TextSecondary
                        )
                        ProviderStatusRow(
                            label = "Enabled",
                            value = if (status.enabled) "Enabled" else "Disabled",
                            valueColor = if (status.enabled) TextPrimary else TextSecondary
                        )
                        ProviderStatusRow(
                            label = "Health",
                            value = healthLabel(status.health),
                            valueColor = healthColor(status.health)
                        )
                        ProviderStatusRow(
                            label = "Circuit breaker",
                            value = circuitLabel(status.circuitState),
                            valueColor = circuitColor(status.circuitState)
                        )
                        ProviderStatusRow(
                            label = "Model",
                            value = status.configuredModel ?: "Auto routing (no fixed model)",
                            monospace = status.configuredModel != null
                        )
                        ProviderStatusRow(
                            label = "Known models",
                            value = if (status.models.isEmpty()) "Not fetched yet" else status.models.size.toString()
                        )
                        ProviderStatusRow(
                            label = "Quota",
                            value = if (status.quota.exhausted) {
                                "Exhausted (reported by HCNSEC)"
                            } else if (status.quota.remaining.isKnown) {
                                "Remaining ${status.quota.remaining.displayText()}"
                            } else {
                                "unknown"
                            },
                            valueColor = if (status.quota.exhausted) ErrorRed else TextSecondary
                        )
                        ProviderStatusRow(
                            label = "Rate limits",
                            value = "limit ${status.rateLimit.limitRequests.displayText()}, remaining ${status.rateLimit.remainingRequests.displayText()}"
                        )
                        ProviderStatusRow(
                            label = "Last latency",
                            value = status.lastLatencyMillis?.let { "$it ms" } ?: "unknown"
                        )
                        status.lastErrorMessage?.let { message ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = message,
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "HCNSEC does not publish a documented balance endpoint, so quota and rate-limit figures are only shown when the API actually reports them. Unknown is never displayed as zero.",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
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
                        Text("Exclusively HCNSEC & Hardware-Backed Keystore", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• Only the configured HCNSEC endpoint receives requests (no OpenAI, Gemini, Anthropic or other third parties).\n• API Keys are encrypted using AES-GCM via AndroidKeyStore.\n• Base URL is permanently bound to ${HcnsecProviderConfig.DEFAULT_BASE_URL}.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
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
                    "This will delete the encrypted key from Android Keystore. You will need to enter an API key again to use SALi-HCNSEC.",
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


@Composable
private fun ProviderStatusRow(
    label: String,
    value: String,
    valueColor: Color = TextPrimary,
    monospace: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextSecondary, fontSize = 12.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            color = valueColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default
        )
    }
}

private fun healthLabel(state: ProviderHealthState): String = when (state) {
    ProviderHealthState.UNKNOWN -> "Unknown"
    ProviderHealthState.AVAILABLE -> "Available"
    ProviderHealthState.DEGRADED -> "Degraded"
    ProviderHealthState.NETWORK_ERROR -> "Network error"
    ProviderHealthState.RATE_LIMITED -> "Rate limited"
    ProviderHealthState.QUOTA_EXHAUSTED -> "Quota exhausted"
    ProviderHealthState.AUTH_ERROR -> "Authentication error"
    ProviderHealthState.DISABLED -> "Disabled"
}

private fun healthColor(state: ProviderHealthState): Color = when (state) {
    ProviderHealthState.AVAILABLE -> SuccessGreen
    ProviderHealthState.UNKNOWN -> TextSecondary
    ProviderHealthState.DISABLED -> TextSecondary
    else -> ErrorRed
}

private fun circuitLabel(state: CircuitState): String = when (state) {
    CircuitState.CLOSED -> "Closed (healthy)"
    CircuitState.OPEN -> "Open (paused)"
    CircuitState.HALF_OPEN -> "Half-open (probing)"
}

private fun circuitColor(state: CircuitState): Color = when (state) {
    CircuitState.CLOSED -> SuccessGreen
    CircuitState.HALF_OPEN -> VioletLight
    CircuitState.OPEN -> ErrorRed
}
