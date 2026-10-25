package com.example.features.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.core.data.SketchToAppRepository
import com.example.core.models.UsageLogRecord
import com.example.core.models.UserProfile
import com.example.core.schema.CanonicalApplicationDocument
import com.example.features.auth.signOutUser
import com.example.features.workspace.AsyncResource
import com.example.ui.components.PipelineStageBadge
import com.example.ui.components.WorkstationTopBar

/**
 * Screen 20: Project Settings & Destructive Cascade Purge
 */
@Composable
fun ProjectSettingsScreen(
    document: CanonicalApplicationDocument?,
    onSaveMetadata: (appName: String, packageName: String) -> Unit,
    onDeleteProject: () -> Unit,
    onBack: () -> Unit
) {
    var appName by remember(document?.projectId) { mutableStateOf(document?.appName.orEmpty()) }
    var packageName by remember(document?.projectId) { mutableStateOf(document?.packageName.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            WorkstationTopBar(
                title = "Project Settings",
                subtitle = "Application metadata, package ID & lifecycle",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("project_settings_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = appName,
                            onValueChange = { appName = it },
                            label = { Text("Generated App Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("project_settings_name_input")
                        )
                        OutlinedTextField(
                            value = packageName,
                            onValueChange = { packageName = it },
                            label = { Text("Android Package Identifier") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("project_settings_package_input")
                        )
                        Button(
                            onClick = { onSaveMetadata(appName, packageName) },
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_project_settings_button")
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Save Project Metadata")
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Danger Zone — Cascade Purge",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            "Deleting this project permanently deletes its canonical document, screens, sketch/Figma design records, generation jobs, build logs, and artifact metadata from Cloud Firestore.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Button(
                            onClick = { confirmDelete = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("delete_project_settings_button")
                        ) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Delete Project Permanently")
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Confirm Permanent Deletion") },
            text = { Text("Delete '${document?.appName ?: "this project"}' and all owned sub-records?") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        onDeleteProject()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

/**
 * Screen 21: Account & Identity Settings (with CredentialManager Sign-Out)
 */
@Composable
fun AccountSettingsScreen(
    userProfile: UserProfile?,
    onSaveDisplayName: (String) -> Unit,
    onSignedOut: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    var displayName by remember(userProfile?.userId) { mutableStateOf(userProfile?.displayName.orEmpty()) }

    Scaffold(
        topBar = {
            WorkstationTopBar(
                title = "Account & Identity",
                subtitle = userProfile?.email ?: "Authenticated Google Identity",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("account_settings_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Developer Profile", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "UID: ${userProfile?.userId ?: "—"}\nPlan Tier: ${(userProfile?.planTier ?: "free").uppercase()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { Text("Display Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("account_display_name_input")
                        )
                        Button(
                            onClick = { onSaveDisplayName(displayName) },
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_account_button")
                        ) {
                            Text("Update Display Name")
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = {
                        signOutUser(
                            credentialManager = credentialManager,
                            scope = scope,
                            onSignOutComplete = onSignedOut
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("sign_out_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Sign Out & Clear Credential State")
                }
            }
        }
    }
}

/**
 * Screen 22: Usage, Quotas & Audit Log Telemetry
 */
@Composable
fun UsageQuotaScreen(
    userProfile: UserProfile?,
    usageLogs: List<UsageLogRecord>,
    onBack: () -> Unit
) {
    val aiUsed = userProfile?.aiCallsUsed ?: 0L
    val buildsUsed = userProfile?.buildsUsed ?: 0L
    val aiMax = SketchToAppRepository.FREE_PLAN_AI_LIMIT
    val buildMax = SketchToAppRepository.FREE_PLAN_BUILD_LIMIT

    Scaffold(
        topBar = {
            WorkstationTopBar(
                title = "Usage & Quotas",
                subtitle = "Per-user AI analysis & isolated worker build limits",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("usage_quota_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Plan Tier: ${(userProfile?.planTier ?: "free").uppercase()}", style = MaterialTheme.typography.titleMedium)

                        Text("Gemini Multimodal Sketch Analyses: $aiUsed / $aiMax", style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(
                            progress = { (aiUsed.toFloat() / aiMax.toFloat()).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text("Isolated APK Build Submissions: $buildsUsed / $buildMax", style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(
                            progress = { (buildsUsed.toFloat() / buildMax.toFloat()).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item {
                Text("Immutable Usage Audit Logs (${usageLogs.size})", style = MaterialTheme.typography.titleMedium)
            }

            if (usageLogs.isEmpty()) {
                item {
                    Text(
                        "No usage events recorded yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(usageLogs, key = { it.id }) { log ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(log.actionType, style = MaterialTheme.typography.titleSmall)
                                Text("Project: ${log.projectId.ifBlank { "—" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            PipelineStageBadge(log.status)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Screen 23: Backend Assistant Agent, Security & Privacy Transparency
 */
@Composable
fun HelpPrivacyScreen(
    assistantState: AsyncResource<String>,
    onAskAssistant: (String) -> Unit,
    onBack: () -> Unit
) {
    var question by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            WorkstationTopBar(
                title = "Assistant, Security & Privacy",
                subtitle = "Gemini 3.5 Flash Architecture Assistant & Retention Policy",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("help_privacy_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Backend & Flutter Architecture Assistant", style = MaterialTheme.typography.titleMedium)
                        }
                        OutlinedTextField(
                            value = question,
                            onValueChange = { question = it },
                            label = { Text("Ask about Flutter state, Firestore schemas, or Docker worker setup") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth().testTag("assistant_question_input")
                        )
                        Button(
                            onClick = { onAskAssistant(question) },
                            enabled = question.isNotBlank() && assistantState !is AsyncResource.Loading,
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("ask_assistant_button")
                        ) {
                            if (assistantState is AsyncResource.Loading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Consulting Gemini 3.5 Flash...")
                            } else {
                                Text("Ask Architecture Assistant")
                            }
                        }

                        when (assistantState) {
                            is AsyncResource.Success -> {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = assistantState.data,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                            is AsyncResource.Error -> {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = assistantState.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Zero-Trust Security & Artifact Retention", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            "• Per-User Cloud Isolation: All 10 Cloud Firestore collections enforce Zero-Trust ownership checks bound to your Firebase Auth UID.\n" +
                                "• Zero-Permission Media Selection: Sketch photos are imported via Android Photo Picker without broad storage permissions.\n" +
                                "• Sandboxed APK Compilation: Untrusted generated Flutter code is compiled exclusively in non-root Docker containers (/build-worker) with CPU/RAM/timeout quotas and SHA-256 verification.\n" +
                                "• Cascade Deletion: Deleting a project purges its canonical document, screens, designs, assets, build logs, and artifact records.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
