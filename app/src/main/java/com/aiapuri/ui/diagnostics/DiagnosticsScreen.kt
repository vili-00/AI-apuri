package com.aiapuri.ui.diagnostics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aiapuri.AiapuriApplication

/**
 * Diagnostics screen that collects non-sensitive app information
 * and generates a shareable report.
 *
 * The report excludes:
 * - Chat content
 * - API keys
 * - Encrypted data
 * - User identifiers
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val application = LocalContext.current.applicationContext as AiapuriApplication
    val viewModel = rememberDiagnosticsViewModel(application)
    val state = viewModel.uiState

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Share button (shown when report is generated)
                    if (state.reportText != null) {
                        IconButton(
                            onClick = { shareDiagnostics(application, state.reportText!!) }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share diagnostics")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "What is included",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    BulletPoint("Device information (model, Android version)")
                    BulletPoint("App version")
                    BulletPoint("Server URL (without API key)")
                    BulletPoint("App settings summary")
                    BulletPoint("Conversation and persona counts")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "What is excluded",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    BulletPoint("Chat messages and conversation content")
                    BulletPoint("API keys and authentication tokens")
                    BulletPoint("Encrypted database contents")
                    BulletPoint("User identifiers")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Generate button
            Button(
                onClick = { viewModel.generateReport() },
                enabled = !state.isGenerating,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate Report")
                }
            }

            // Error
            state.errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.dismissError() }) { Text("Dismiss") }
                    }
                }
            }

            // Report preview
            state.reportText?.let { report ->
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Report Preview",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        androidx.compose.material3.HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = report,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { shareDiagnostics(application, report) },
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share Report")
                }
            }
        }
    }
}

/**
 * Share the diagnostics report via an intent.
 */
private fun shareDiagnostics(application: AiapuriApplication, reportText: String) {
    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_SUBJECT, "AI-apuri Diagnostics Report")
        putExtra(android.content.Intent.EXTRA_TEXT, reportText)
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    application.startActivity(
        android.content.Intent.createChooser(shareIntent, "Share diagnostics report")
    )
}

/**
 * A simple bullet point row.
 */
@Composable
private fun BulletPoint(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "• ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Create or retrieve the diagnostics ViewModel.
 */
@Composable
fun rememberDiagnosticsViewModel(application: AiapuriApplication): DiagnosticsViewModel {
    return remember { DiagnosticsViewModel(application) }
}
