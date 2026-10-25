package com.example.features.build_lab

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.sp
import com.example.core.models.BuildJobRecord
import com.example.core.models.GeneratedAppRecord
import com.example.core.schema.DiagnosticSeverity
import com.example.core.schema.SchemaDiagnostic
import com.example.features.ai.GeminiAgentsService
import com.example.features.code_generation.ExportedArchiveResult
import com.example.features.code_generation.GeneratedFlutterProject
import com.example.features.code_generation.ZipAndArtifactExporter
import com.example.ui.components.PipelineStageBadge
import com.example.ui.components.StatusFeedbackBanners
import com.example.ui.components.SyntaxHighlightedCodeBlock
import com.example.ui.components.WorkstationTopBar
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlinx.coroutines.launch

/**
 * Screen 15: Generated Flutter Project Explorer, Code Viewer & Verified ZIP Exporter
 */
@Composable
fun FlutterCodeViewerScreen(
    generatedProject: GeneratedFlutterProject?,
    exportedZip: ExportedArchiveResult?,
    isBusy: Boolean,
    statusBanner: String,
    errorBanner: String,
    onDismissBanner: () -> Unit,
    onGenerateAndPackageZip: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenBuildLab: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedPath by remember(generatedProject) {
        mutableStateOf(generatedProject?.files?.firstOrNull()?.relativePath.orEmpty())
    }
    var localNotice by remember { mutableStateOf("") }

    val saveZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { destUri ->
        val zip = exportedZip?.file
        if (destUri != null && zip != null && zip.exists()) {
            scope.launch {
                val res = ZipAndArtifactExporter.copyFileToDocumentUri(context, zip, destUri)
                localNotice = res.fold(
                    onSuccess = { bytes -> "Saved verified Flutter ZIP (${bytes / 1024} KB) to device storage." },
                    onFailure = { err -> err.localizedMessage ?: "Failed to save ZIP." }
                )
            }
        }
    }

    Scaffold(
        topBar = {
            WorkstationTopBar(
                title = "Flutter Code & ZIP Export",
                subtitle = generatedProject?.let { "${it.files.size} files • ${it.projectSlug}" } ?: "Deterministic Dart Generator",
                onBack = onBack,
                actions = {
                    OutlinedButton(onClick = onOpenDiagnostics, modifier = Modifier.padding(end = 8.dp).testTag("open_diagnostics_button")) {
                        Text("Diagnostics (${generatedProject?.diagnostics?.size ?: 0})")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("flutter_code_viewer_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                StatusFeedbackBanners(
                    statusMessage = localNotice.ifBlank { statusBanner },
                    errorMessage = errorBanner,
                    onDismiss = {
                        localNotice = ""
                        onDismissBanner()
                    }
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onGenerateAndPackageZip,
                        enabled = !isBusy,
                        modifier = Modifier.weight(1f).height(48.dp).testTag("package_flutter_zip_button")
                    ) {
                        Icon(Icons.Filled.FolderZip, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (isBusy) "Packaging..." else "Generate & ZIP")
                    }
                    OutlinedButton(
                        onClick = onOpenBuildLab,
                        modifier = Modifier.height(48.dp).testTag("code_to_build_lab_button")
                    ) {
                        Icon(Icons.Filled.Build, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Build Lab")
                    }
                }
            }

            if (exportedZip != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Verified Source Archive Ready", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Text(
                                "${exportedZip.file.name} (${exportedZip.sizeBytes} bytes)\nSHA-256: ${exportedZip.sha256Hex.take(32)}...",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { saveZipLauncher.launch(exportedZip.file.name) },
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("save_zip_to_device_button")
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Save .zip")
                                }
                                OutlinedButton(
                                    onClick = {
                                        ZipAndArtifactExporter.shareExportedFile(
                                            context = context,
                                            file = exportedZip.file,
                                            mimeType = "application/zip",
                                            chooserTitle = "Share Flutter Source ZIP"
                                        ).onFailure { err ->
                                            localNotice = err.localizedMessage ?: "Unable to share ZIP."
                                        }
                                    },
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("share_zip_button")
                                ) {
                                    Icon(Icons.Filled.Share, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Share .zip")
                                }
                            }
                        }
                    }
                }
            }

            if (generatedProject != null && generatedProject.files.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        generatedProject.files.forEach { file ->
                            FilterChip(
                                selected = file.relativePath == selectedPath,
                                onClick = { selectedPath = file.relativePath },
                                label = { Text(file.relativePath) }
                            )
                        }
                    }
                }

                val activeFile = generatedProject.files.firstOrNull { it.relativePath == selectedPath }
                    ?: generatedProject.files.first()

                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = activeFile.relativePath,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText(activeFile.relativePath, activeFile.content))
                                        localNotice = "Copied ${activeFile.relativePath} to clipboard."
                                    },
                                    modifier = Modifier.size(48.dp).testTag("copy_code_button")
                                ) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy file source")
                                }
                            }
                            Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                SyntaxHighlightedCodeBlock(source = activeFile.content)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Screen 16: Schema Validation & Flutter Code Quality Diagnostics
 */
@Composable
fun DiagnosticsScreen(
    diagnostics: List<SchemaDiagnostic>,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            WorkstationTopBar(
                title = "Validation & Diagnostics",
                subtitle = "${diagnostics.count { it.severity == DiagnosticSeverity.ERROR }} error(s), ${diagnostics.count { it.severity == DiagnosticSeverity.WARNING }} warning(s)",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("diagnostics_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (diagnostics.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "All canonical schema, route graph, identifier, and asset invariants passed with zero issues.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            } else {
                items(diagnostics) { item ->
                    val isErr = item.severity == DiagnosticSeverity.ERROR
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isErr) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = BorderStroke(1.dp, if (isErr) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.WarningAmber,
                                    contentDescription = null,
                                    tint = if (isErr) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("[${item.severity.name}] ${item.code}", style = MaterialTheme.typography.titleSmall)
                            }
                            Text(item.message, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Screen 17: Isolated Flutter APK Build Lab
 */
@Composable
fun BuildLabScreen(
    buildJobs: List<BuildJobRecord>,
    isBusy: Boolean,
    errorBanner: String,
    statusBanner: String,
    onDismissBanner: () -> Unit,
    onSubmitBuild: (buildProfile: String) -> Unit,
    onOpenLogs: (buildJobId: String) -> Unit,
    onOpenArtifacts: () -> Unit,
    onOpenCodeExport: () -> Unit,
    onBack: () -> Unit
) {
    var buildProfile by remember { mutableStateOf("debug") }
    val remoteConfigured = remember { GeminiAgentsService.isRemoteWorkerConfigured() }

    Scaffold(
        topBar = {
            WorkstationTopBar(
                title = "Isolated APK Build Lab",
                subtitle = if (remoteConfigured) "Remote Docker Worker Connected" else "Source Export Active • Worker Unconfigured",
                onBack = onBack,
                actions = {
                    OutlinedButton(onClick = onOpenArtifacts, modifier = Modifier.padding(end = 8.dp).testTag("open_artifacts_button")) {
                        Text("Artifacts")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("build_lab_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                StatusFeedbackBanners(
                    statusMessage = statusBanner,
                    errorMessage = errorBanner,
                    onDismiss = onDismissBanner
                )
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Android APK Compilation Pipeline", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (remoteConfigured) {
                                "Connected to isolated Docker Flutter worker. Submitting a build runs flutter pub get, flutter analyze, flutter test, and flutter build apk with SHA-256 verification."
                            } else {
                                "Honest Worker Disclosure: Untrusted user-generated Flutter code is never compiled inside the mobile handset. Configure BACKEND_API_URL in AI Studio Secrets pointing to /backend + /build-worker for remote APK builds, or export the complete verified Flutter source ZIP immediately."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("debug", "release").forEach { prof ->
                                FilterChip(
                                    selected = buildProfile == prof,
                                    onClick = { buildProfile = prof },
                                    label = { Text("${prof.replaceFirstChar { it.uppercaseChar() }} APK") }
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onSubmitBuild(buildProfile) },
                                enabled = !isBusy,
                                modifier = Modifier.weight(1f).height(50.dp).testTag("submit_apk_build_button")
                            ) {
                                if (isBusy) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Running...")
                                } else {
                                    Icon(Icons.Filled.Build, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Queue ${buildProfile.uppercase()} Build")
                                }
                            }
                            OutlinedButton(
                                onClick = onOpenCodeExport,
                                modifier = Modifier.height(50.dp).testTag("build_lab_export_zip_button")
                            ) {
                                Text("Export ZIP")
                            }
                        }
                    }
                }
            }

            item {
                Text("Build Job History (${buildJobs.size})", style = MaterialTheme.typography.titleMedium)
            }

            if (buildJobs.isEmpty()) {
                item {
                    Text(
                        "No APK build jobs queued for this project yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(buildJobs, key = { it.id }) { job ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth().clickable { onOpenLogs(job.id) }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Job ${job.id.takeLast(8)} • ${job.buildProfile.uppercase()}",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    "Worker: ${job.workerMode} • Flutter ${job.flutterVersion} • Exit ${job.exitCode}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            PipelineStageBadge(job.status)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Screen 18: Sanitized Worker Build Logs Viewer
 */
@Composable
fun BuildLogsScreen(
    buildJobs: List<BuildJobRecord>,
    selectedJobId: String,
    onBack: () -> Unit
) {
    val activeJob = buildJobs.firstOrNull { it.id == selectedJobId } ?: buildJobs.firstOrNull()

    Scaffold(
        topBar = {
            WorkstationTopBar(
                title = "Worker Build Logs",
                subtitle = activeJob?.let { "Job ${it.id.takeLast(8)} • ${it.status.uppercase()}" } ?: "No Build Logs",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("build_logs_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (activeJob == null) {
                item {
                    Text("No build job logs recorded yet.")
                }
            } else {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Sanitized Build Output", style = MaterialTheme.typography.titleSmall)
                                }
                                PipelineStageBadge(activeJob.status)
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = activeJob.sanitizedLogs.ifBlank { "Awaiting log stream..." },
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Screen 19: Verified APK & Source Artifact Center
 */
@Composable
fun ArtifactsScreen(
    artifacts: List<GeneratedAppRecord>,
    exportedZip: ExportedArchiveResult?,
    downloadProgress: Int?,
    onDownloadApk: (GeneratedAppRecord) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            WorkstationTopBar(
                title = "Verified Artifacts",
                subtitle = "SHA-256 verified APK binaries & source archives",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("artifacts_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (downloadProgress != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Downloading & verifying SHA-256 checksum ($downloadProgress%)...", style = MaterialTheme.typography.labelMedium)
                        LinearProgressIndicator(progress = { downloadProgress / 100f }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            if (exportedZip != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Verified, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("Flutter Source Archive (.zip)", style = MaterialTheme.typography.titleMedium)
                            }
                            Text(
                                "${exportedZip.file.name} • ${exportedZip.sizeBytes} bytes\nSHA-256: ${exportedZip.sha256Hex}",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = {
                                    ZipAndArtifactExporter.shareExportedFile(
                                        context = context,
                                        file = exportedZip.file,
                                        mimeType = "application/zip",
                                        chooserTitle = "Share Flutter Source ZIP"
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Share Verified Source ZIP")
                            }
                        }
                    }
                }
            }

            if (artifacts.isEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No verified APK binaries registered for this project yet. APK artifacts appear here only after an isolated remote worker build exits 0 with a non-empty SHA-256 verified binary.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(artifacts, key = { it.id }) { art ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("${art.appName} (${art.buildProfile.uppercase()} APK)", style = MaterialTheme.typography.titleMedium)
                                PipelineStageBadge(if (art.verified) "verified" else "unverified")
                            }
                            Text(
                                "Package: ${art.packageName} • v${art.versionName} • ${art.sizeBytes / 1024} KB\nSHA-256: ${art.checksumSha256}",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { onDownloadApk(art) },
                                enabled = art.verified && downloadProgress == null,
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("download_verified_apk_button")
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Download & Verify APK")
                            }
                        }
                    }
                }
            }
        }
    }
}
