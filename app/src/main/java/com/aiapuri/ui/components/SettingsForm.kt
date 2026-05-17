package com.aiapuri.ui.components

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCameraBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.aiapuri.core.model.ModelInfo
import com.aiapuri.ui.settings.SettingsUiState
import com.aiapuri.ui.settings.TestConnectionState
import com.aiapuri.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch

/**
 * Shared settings form used by both Onboarding and Settings screens.
 */
@Composable
fun SettingsForm(
    viewModel: SettingsViewModel,
    onSaved: () -> Unit,
    showOnboardingHint: Boolean = false,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var saveTriggered by remember { mutableStateOf(false) }

    // Handle save result via side effect
    LaunchedEffect(saveTriggered) {
        if (saveTriggered) {
            val success = viewModel.save()
            if (success) onSaved()
            saveTriggered = false
        }
    }

    val state = viewModel.uiState

    // Clear all data confirmation dialog
    if (state.showClearDataDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { viewModel.hideClearDataDialog() },
            icon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Clear All Data") },
            text = {
                Text(
                    "This will permanently delete all conversations, messages, personas, " +
                    "server settings, API keys, and app lock settings. The app will close " +
                    "and restart. Reopening the app shows the onboarding screen. " +
                    "This action cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.onClearDataConfirmed() },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideClearDataDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {

        if (showOnboardingHint) {
            Text(
                text = "Configure your local llama.cpp server to get started.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // ---- Server URL ----
        Text(
            text = "Server URL",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        OutlinedTextField(
            value = state.baseUrl,
            onValueChange = viewModel::onBaseUrlChanged,
            label = { Text("e.g. http://100.x.y.z:8080") },
            isError = state.urlError != null,
            supportingText = state.urlError?.let { { Text(it) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ---- API Key ----
        Text(
            text = "API Key",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        OutlinedTextField(
            value = state.apiKey,
            onValueChange = viewModel::onApiKeyChanged,
            label = { Text("Bearer token for llama.cpp") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (state.showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = viewModel::toggleApiKeyVisibility) {
                    Icon(
                        imageVector = if (state.showApiKey) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (state.showApiKey) "Hide API key" else "Show API key"
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ---- No-key mode warning ----
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.warningColor(),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "No-key development mode",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Skip API key authentication. Only use on trusted local networks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.allowNoApiKey,
                    onCheckedChange = viewModel::onAllowNoApiKeyChanged,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ---- Test connection ----
        TestConnectionCard(
            state = state.testConnectionState,
            fetchedModels = state.fetchedModels,
            connectionErrorMessage = state.connectionErrorMessage,
            onTest = viewModel::testConnection,
            onRefreshModels = viewModel::refreshModels
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ---- Default Model ----
        Text(
            text = "Default Model",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        ModelSelector(
            currentModel = state.defaultModel,
            availableModels = state.fetchedModels,
            onModelChanged = viewModel::onDefaultModelChanged,
            onModelSelected = viewModel::onSelectModel
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ---- Privacy & Security section ----
        PrivacyAndSecuritySection(
            viewModel = viewModel,
            showOnboardingHint = showOnboardingHint
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ---- Save button ----
        Button(
            onClick = { saveTriggered = true },
            enabled = !state.isSaving && state.urlError == null,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Save Settings")
            }
        }
    }
}

/**
 * Test connection card showing connection status with detailed feedback.
 */
@Composable
private fun TestConnectionCard(
    state: TestConnectionState,
    fetchedModels: List<ModelInfo>,
    connectionErrorMessage: String?,
    onTest: () -> Unit,
    onRefreshModels: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (state) {
                    is TestConnectionState.Idle -> {
                        Text(
                            text = "Test Connection",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onTest) {
                            Text("Test")
                        }
                    }
                    is TestConnectionState.Testing -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Testing connection…",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    is TestConnectionState.Success -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Connected successfully",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (state.models.isNotEmpty()) {
                                Text(
                                    text = "${state.models.size} model${if (state.models.size > 1) "s" else ""} found",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        TextButton(onClick = onRefreshModels) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh models")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Refresh")
                        }
                    }
                    is TestConnectionState.Error -> {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        TextButton(onClick = onTest) {
                            Text("Retry")
                        }
                    }
                }
            }

            // Show fetched model list when available
            if (fetchedModels.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                    )
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "Available Models",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        fetchedModels.forEach { model ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = model.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = model.id,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Model selector supporting both dropdown selection from fetched models
 * and manual text entry.
 */
@Composable
private fun ModelSelector(
    currentModel: String,
    availableModels: List<ModelInfo>,
    onModelChanged: (String) -> Unit,
    onModelSelected: (ModelInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var manualText by remember { mutableStateOf(currentModel) }

    // Sync manual text when currentModel changes externally
    LaunchedEffect(currentModel) {
        manualText = currentModel
    }

    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = manualText,
            onValueChange = {
                manualText = it
                onModelChanged(it)
            },
            label = { Text("e.g. qwen3.6-27b") },
            supportingText = {
                Text(
                    text = if (availableModels.isNotEmpty()) {
                        "Select from list or type manually"
                    } else {
                        "Enter model name manually"
                    }
                )
            },
            trailingIcon = {
                if (availableModels.isNotEmpty()) {
                    IconButton(
                        onClick = { expanded = !expanded },
                        enabled = availableModels.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Select model"
                        )
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // Dropdown for model selection
        if (availableModels.isNotEmpty()) {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                availableModels.forEach { model ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(text = model.displayName, style = MaterialTheme.typography.bodyMedium)
                                Text(text = model.id, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        onClick = {
                            onModelSelected(model)
                            manualText = model.id
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

/** Extension to get a warm warning color from the scheme. */
private fun androidx.compose.material3.ColorScheme.warningColor(): Color {
    return Color(0xFFFFA000)
}

/**
 * Privacy & Security section of the settings form.
 *
 * Shows toggles for app lock and screenshot blocking,
 * plus a clear-all-data action. Hidden during onboarding.
 */
@Composable
private fun PrivacyAndSecuritySection(
    viewModel: SettingsViewModel,
    showOnboardingHint: Boolean,
    modifier: Modifier = Modifier
) {
    // Don't show privacy controls during onboarding — user hasn't configured the server yet
    if (showOnboardingHint) return

    val state = viewModel.uiState

    Text(
        text = "Privacy & Security",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // App Lock toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "App Lock",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Require biometric or device credential to unlock",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.appLockEnabled,
                    onCheckedChange = { enabled ->
                        viewModel.onAppLockToggled(enabled)
                        viewModel.saveAppSettingsState()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Screenshot blocking toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoCameraBack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Block Screenshots",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Prevent screenshots and screen recording",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.blockScreenshots,
                    onCheckedChange = { enabled ->
                        viewModel.onBlockScreenshotsToggled(enabled)
                        viewModel.saveAppSettingsState()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Clear all data button
            TextButton(
                onClick = { viewModel.showClearDataDialog() },
                modifier = Modifier.align(Alignment.Start)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Clear All Data",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
