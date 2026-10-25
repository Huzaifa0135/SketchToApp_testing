package com.example.features.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DesignServices
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.models.ProjectRecord
import com.example.core.models.UserProfile
import com.example.features.workspace.AsyncResource
import com.example.ui.components.PipelineStageBadge
import com.example.ui.components.WorkstationTopBar
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Screen 4: Workstation Dashboard
 */
@Composable
fun DashboardScreen(
    userProfile: UserProfile?,
    projectsState: AsyncResource<List<ProjectRecord>>,
    onCreateWithSource: (name: String, description: String, pkg: String, sourceType: String) -> Unit,
    onOpenProject: (ProjectRecord) -> Unit,
    onNavigateProjects: () -> Unit,
    onNavigateUsage: () -> Unit,
    onNavigateAccount: () -> Unit,
    onNavigateHelp: () -> Unit
) {
    var createModalSource by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            WorkstationTopBar(
                title = "SketchToApp AI",
                subtitle = "Welcome, ${userProfile?.displayName?.ifBlank { "Developer" } ?: "Developer"}",
                actions = {
                    IconButton(onClick = onNavigateHelp, modifier = Modifier.testTag("nav_help_button")) {
                        Icon(Icons.Filled.HelpOutline, contentDescription = "Help & Assistant")
                    }
                    IconButton(onClick = onNavigateAccount, modifier = Modifier.testTag("nav_account_button")) {
                        Icon(Icons.Filled.PersonOutline, contentDescription = "Account Settings")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = true,
                    onClick = {},
                    icon = { Icon(Icons.Filled.AutoFixHigh, contentDescription = "Studio") },
                    label = { Text("Studio") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateProjects,
                    icon = { Icon(Icons.Filled.FolderOpen, contentDescription = "Projects") },
                    label = { Text("Projects") },
                    modifier = Modifier.testTag("bottom_nav_projects")
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateUsage,
                    icon = { Icon(Icons.Filled.QueryStats, contentDescription = "Usage") },
                    label = { Text("Quotas") },
                    modifier = Modifier.testTag("bottom_nav_usage")
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("dashboard_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = "Create Your Android App",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Start from a hand-drawn sketch, an authorized Figma file, or a blank multi-screen canvas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                LaunchActionCard(
                    icon = Icons.Filled.Draw,
                    title = "Draw or Upload Sketch",
                    subtitle = "Hand-drawn wireframe -> Gemini 3.1 Pro -> Canonical UI",
                    tag = "quick_action_sketch",
                    onClick = { createModalSource = "sketch" }
                )
            }

            item {
                LaunchActionCard(
                    icon = Icons.Filled.DesignServices,
                    title = "Import Figma Design",
                    subtitle = "Authorized Figma file URL -> Frame selector -> Flutter code",
                    tag = "quick_action_figma",
                    onClick = { createModalSource = "figma" }
                )
            }

            item {
                LaunchActionCard(
                    icon = Icons.Filled.Add,
                    title = "Blank Multi-Screen App",
                    subtitle = "Build directly in the touch-optimized Visual UI Editor",
                    tag = "quick_action_blank",
                    onClick = { createModalSource = "blank" }
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recent Projects", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onNavigateProjects) {
                        Text("Manage All")
                    }
                }
            }

            when (projectsState) {
                is AsyncResource.Loading, AsyncResource.Idle -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }

                is AsyncResource.Error -> {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = projectsState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }

                is AsyncResource.Success -> {
                    if (projectsState.data.isEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "No projects yet",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "Tap 'Draw or Upload Sketch' above to build your first Flutter Android app.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(projectsState.data.take(6), key = { it.id }) { proj ->
                            ProjectSummaryCard(
                                project = proj,
                                onOpen = { onOpenProject(proj) },
                                onDuplicate = null,
                                onDelete = null
                            )
                        }
                    }
                }
            }
        }
    }

    if (createModalSource != null) {
        NewProjectDialog(
            initialSourceType = createModalSource!!,
            onDismiss = { createModalSource = null },
            onConfirm = { name, desc, pkg, src ->
                createModalSource = null
                onCreateWithSource(name, desc, pkg, src)
            }
        )
    }
}

@Composable
private fun LaunchActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tag: String,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Screen 5: Project Creation and Management
 */
@Composable
fun ProjectManagementScreen(
    projectsState: AsyncResource<List<ProjectRecord>>,
    onBack: () -> Unit,
    onCreateProject: (String, String, String, String) -> Unit,
    onOpenProject: (ProjectRecord) -> Unit,
    onDuplicateProject: (String) -> Unit,
    onDeleteProject: (String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var projectPendingDelete by remember { mutableStateOf<ProjectRecord?>(null) }

    Scaffold(
        topBar = {
            WorkstationTopBar(
                title = "Projects",
                subtitle = "Create, resume, duplicate, or purge saved projects",
                onBack = onBack
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("fab_new_project")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Create New Project")
            }
        }
    ) { innerPadding ->
        when (projectsState) {
            is AsyncResource.Loading, AsyncResource.Idle -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is AsyncResource.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(projectsState.message, color = MaterialTheme.colorScheme.error)
                }
            }

            is AsyncResource.Success -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .testTag("project_management_screen"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (projectsState.data.isEmpty()) {
                        item {
                            Text(
                                text = "No saved projects yet. Tap + to create a project.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        items(projectsState.data, key = { it.id }) { proj ->
                            ProjectSummaryCard(
                                project = proj,
                                onOpen = { onOpenProject(proj) },
                                onDuplicate = { onDuplicateProject(proj.id) },
                                onDelete = { projectPendingDelete = proj }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        NewProjectDialog(
            initialSourceType = "sketch",
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, desc, pkg, src ->
                showCreateDialog = false
                onCreateProject(name, desc, pkg, src)
            }
        )
    }

    if (projectPendingDelete != null) {
        AlertDialog(
            onDismissRequest = { projectPendingDelete = null },
            title = { Text("Delete Project?") },
            text = {
                Text(
                    "Delete '${projectPendingDelete!!.name}' and purge all associated screens, designs, generation jobs, build logs, and artifact records?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = projectPendingDelete!!.id
                        projectPendingDelete = null
                        onDeleteProject(id)
                    },
                    modifier = Modifier.testTag("confirm_delete_project_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { projectPendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ProjectSummaryCard(
    project: ProjectRecord,
    onOpen: () -> Unit,
    onDuplicate: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.US) }
    val updatedDate = (project.updatedAt ?: project.createdAt)?.toDate()
    val formattedTime = updatedDate?.let { dateFormat.format(it) } ?: "Just now"

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .testTag("project_card_${project.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                PipelineStageBadge(project.stage)
            }

            if (project.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = project.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${project.screenCount} screen(s) • ${project.sourceType.uppercase()} • $formattedTime",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row {
                    if (onDuplicate != null) {
                        IconButton(onClick = onDuplicate, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate project")
                        }
                    }
                    if (onDelete != null) {
                        IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                            Icon(
                                Icons.Filled.DeleteOutline,
                                contentDescription = "Delete project",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NewProjectDialog(
    initialSourceType: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String, packageName: String, sourceType: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("com.generated.myapp") }
    var sourceType by remember { mutableStateOf(initialSourceType) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New App Project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        val slug = it.lowercase().replace(Regex("[^a-z0-9]+"), "").take(20)
                        if (slug.isNotEmpty()) packageName = "com.generated.$slug"
                    },
                    label = { Text("Application Name") },
                    placeholder = { Text("e.g. Habit Pulse") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_project_name_input")
                )
                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = { Text("Target Android Package") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Brief Description (Optional)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("sketch", "figma", "blank").forEach { option ->
                        OutlinedButton(
                            onClick = { sourceType = option },
                            border = BorderStroke(
                                width = if (sourceType == option) 2.dp else 1.dp,
                                color = if (sourceType == option) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(option.replaceFirstChar { it.uppercaseChar() })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        name.ifBlank { "My Sketch App" },
                        description,
                        packageName.ifBlank { "com.generated.myapp" },
                        sourceType
                    )
                },
                modifier = Modifier.testTag("confirm_create_project_button")
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
