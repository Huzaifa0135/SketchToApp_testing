package com.example.features.visual_editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.schema.CanonicalApplicationDocument
import com.example.core.schema.CanonicalComponent
import com.example.core.schema.CanonicalScreen
import com.example.core.schema.CanonicalThemeTokens
import com.example.core.schema.ComponentType
import com.example.core.schema.FormValidationRule
import com.example.core.schema.NavActionType
import com.example.features.workspace.ActiveEditorState
import com.example.ui.components.CanonicalPhoneFrameRenderer
import com.example.ui.components.StatusFeedbackBanners
import com.example.ui.components.WorkstationTopBar

/**
 * Screen 9: Visual UI Editor Workstation & Contextual Component Inspector
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VisualEditorScreen(
    editorState: ActiveEditorState,
    onSelectScreen: (String) -> Unit,
    onSelectComponent: (String) -> Unit,
    onAddComponent: (ComponentType) -> Unit,
    onUpdateSelectedComponent: ((CanonicalComponent) -> CanonicalComponent) -> Unit,
    onMoveSelected: (Int) -> Unit,
    onDuplicateSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit,
    onDismissBanner: () -> Unit,
    onNavigateSketchImport: () -> Unit,
    onNavigateComponentTree: () -> Unit,
    onNavigateScreenManager: () -> Unit,
    onNavigateScreenFlow: () -> Unit,
    onNavigateTheme: () -> Unit,
    onNavigatePreview: () -> Unit,
    onNavigateCodeViewer: () -> Unit,
    onNavigateBuildLab: () -> Unit,
    onNavigateProjectSettings: () -> Unit,
    onBack: () -> Unit
) {
    val doc = editorState.document
    val activeScreen = doc?.screens?.firstOrNull { it.id == editorState.selectedScreenId }
        ?: doc?.screens?.firstOrNull()
    val selectedComp = activeScreen?.components?.firstOrNull { it.id == editorState.selectedComponentId }

    Scaffold(
        topBar = {
            WorkstationTopBar(
                title = doc?.appName ?: "Visual UI Editor",
                subtitle = if (editorState.hasUnsavedChanges) "Unsaved schema edits" else "Synced with Cloud Firestore",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onUndo, enabled = editorState.undoStack.isNotEmpty(), modifier = Modifier.testTag("editor_undo_button")) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = onRedo, enabled = editorState.redoStack.isNotEmpty(), modifier = Modifier.testTag("editor_redo_button")) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }
                    IconButton(onClick = onSave, enabled = !editorState.isSaving, modifier = Modifier.testTag("editor_save_button")) {
                        Icon(
                            Icons.Filled.Save,
                            contentDescription = "Save document",
                            tint = if (editorState.hasUnsavedChanges) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onNavigateProjectSettings, modifier = Modifier.testTag("editor_project_settings_button")) {
                        Icon(Icons.Filled.Settings, contentDescription = "Project settings")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (doc == null || activeScreen == null) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("Loading canonical project document...")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("visual_editor_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                StatusFeedbackBanners(
                    statusMessage = editorState.statusBanner,
                    errorMessage = editorState.errorBanner,
                    onDismiss = onDismissBanner
                )
            }

            // Workstation Navigation Strip
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onNavigateSketchImport, modifier = Modifier.testTag("editor_open_sketch_btn")) {
                        Icon(Icons.Filled.Draw, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Import Sketch")
                    }
                    OutlinedButton(onClick = onNavigatePreview, modifier = Modifier.testTag("editor_open_preview_btn")) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Live Preview")
                    }
                    OutlinedButton(onClick = onNavigateCodeViewer, modifier = Modifier.testTag("editor_open_code_btn")) {
                        Icon(Icons.Filled.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Flutter Code")
                    }
                    Button(onClick = onNavigateBuildLab, modifier = Modifier.testTag("editor_open_build_btn")) {
                        Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Build APK")
                    }
                    OutlinedButton(onClick = onNavigateComponentTree) {
                        Icon(Icons.Filled.AccountTree, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Tree")
                    }
                    OutlinedButton(onClick = onNavigateScreenManager) {
                        Icon(Icons.Filled.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Screens (${doc.screens.size})")
                    }
                    OutlinedButton(onClick = onNavigateScreenFlow) {
                        Icon(Icons.Filled.Hub, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Flow")
                    }
                    OutlinedButton(onClick = onNavigateTheme) {
                        Icon(Icons.Filled.Palette, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Theme")
                    }
                }
            }

            // Screen Selector Chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    doc.screens.forEach { scr ->
                        FilterChip(
                            selected = scr.id == activeScreen.id,
                            onClick = { onSelectScreen(scr.id) },
                            label = { Text(if (scr.id == doc.initialScreenId) "★ ${scr.name}" else scr.name) }
                        )
                    }
                }
            }

            // Add Component Palette
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Add Widget to '${activeScreen.name}'", style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                ComponentType.TEXT,
                                ComponentType.BUTTON,
                                ComponentType.TEXT_FIELD,
                                ComponentType.CARD,
                                ComponentType.IMAGE,
                                ComponentType.ROW,
                                ComponentType.NAV_BAR,
                                ComponentType.DIVIDER
                            ).forEach { type ->
                                OutlinedButton(
                                    onClick = { onAddComponent(type) },
                                    modifier = Modifier.testTag("add_widget_${type.name.lowercase()}")
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(type.displayLabel)
                                }
                            }
                        }
                    }
                }
            }

            // Live Canonical Phone Canvas
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.widthIn(max = 420.dp)) {
                        CanonicalPhoneFrameRenderer(
                            screen = activeScreen,
                            themeTokens = doc.theme,
                            selectedComponentId = editorState.selectedComponentId,
                            interactiveMode = false,
                            onSelectComponent = { onSelectComponent(it.id) }
                        )
                    }
                }
            }

            // Contextual Component Inspector
            if (selectedComp != null) {
                item {
                    ComponentInspectorCard(
                        component = selectedComp,
                        screens = doc.screens,
                        onUpdate = onUpdateSelectedComponent,
                        onMoveUp = { onMoveSelected(-1) },
                        onMoveDown = { onMoveSelected(1) },
                        onDuplicate = onDuplicateSelected,
                        onDelete = onDeleteSelected
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ComponentInspectorCard(
    component: CanonicalComponent,
    screens: List<CanonicalScreen>,
    onUpdate: ((CanonicalComponent) -> CanonicalComponent) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Inspector • ${component.type.displayLabel}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Row {
                    IconButton(onClick = onMoveUp, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = "Move widget up")
                    }
                    IconButton(onClick = onMoveDown, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.ArrowDownward, contentDescription = "Move widget down")
                    }
                    IconButton(onClick = onDuplicate, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate widget")
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete widget", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            OutlinedTextField(
                value = component.label,
                onValueChange = { txt -> onUpdate { it.copy(label = txt) } },
                label = { Text("Primary Label / Text") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("inspector_label_input")
            )

            if (component.type in setOf(ComponentType.CARD, ComponentType.CONTAINER, ComponentType.LIST, ComponentType.ROW, ComponentType.COLUMN)) {
                OutlinedTextField(
                    value = component.secondaryText,
                    onValueChange = { txt -> onUpdate { it.copy(secondaryText = txt) } },
                    label = { Text("Secondary / Supporting Text") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (component.type == ComponentType.TEXT_FIELD) {
                OutlinedTextField(
                    value = component.hint,
                    onValueChange = { txt -> onUpdate { it.copy(hint = txt) } },
                    label = { Text("Placeholder Hint") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Form Validation Rule", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FormValidationRule.entries.forEach { rule ->
                        FilterChip(
                            selected = component.formRule == rule,
                            onClick = {
                                onUpdate {
                                    it.copy(
                                        formRule = rule,
                                        formRequired = rule != FormValidationRule.NONE
                                    )
                                }
                            },
                            label = { Text(rule.label) }
                        )
                    }
                }
            }

            if (component.type == ComponentType.BUTTON) {
                Text("Navigation Action", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NavActionType.entries.forEach { action ->
                        FilterChip(
                            selected = component.navActionType == action,
                            onClick = {
                                onUpdate {
                                    it.copy(
                                        navActionType = action,
                                        navConfirmedByUser = true,
                                        targetScreenId = if (action == NavActionType.NAVIGATE_TO_SCREEN && it.targetScreenId.isBlank()) {
                                            screens.firstOrNull()?.id.orEmpty()
                                        } else it.targetScreenId
                                    )
                                }
                            },
                            label = { Text(action.name) }
                        )
                    }
                }
                if (component.navActionType == NavActionType.NAVIGATE_TO_SCREEN) {
                    Text("Target Screen", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        screens.forEach { target ->
                            FilterChip(
                                selected = component.targetScreenId == target.id,
                                onClick = {
                                    onUpdate { it.copy(targetScreenId = target.id, navConfirmedByUser = true) }
                                },
                                label = { Text(target.name) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Screen 10: Hierarchical Component Tree & Reordering View
 */
@Composable
fun ComponentTreeScreen(
    editorState: ActiveEditorState,
    onSelectScreen: (String) -> Unit,
    onSelectComponent: (String) -> Unit,
    onMoveSelected: (Int) -> Unit,
    onDuplicateSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onBack: () -> Unit
) {
    val doc = editorState.document
    val activeScreen = doc?.screens?.firstOrNull { it.id == editorState.selectedScreenId } ?: doc?.screens?.firstOrNull()

    Scaffold(
        topBar = {
            WorkstationTopBar(
                title = "Component Hierarchy",
                subtitle = activeScreen?.let { "${it.name} (${it.components.size} widgets)" },
                onBack = onBack
            )
        }
    ) { innerPadding ->
        if (doc == null || activeScreen == null) return@Scaffold
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("component_tree_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    doc.screens.forEach { scr ->
                        FilterChip(
                            selected = scr.id == activeScreen.id,
                            onClick = { onSelectScreen(scr.id) },
                            label = { Text(scr.name) }
                        )
                    }
                }
            }

            itemsIndexed(activeScreen.components, key = { _, c -> c.id }) { index, comp ->
                val isSelected = comp.id == editorState.selectedComponentId
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectComponent(comp.id) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("#${index + 1} ${comp.type.displayLabel}", style = MaterialTheme.typography.titleSmall)
                            Text(comp.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (isSelected) {
                            IconButton(onClick = { onMoveSelected(-1) }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up")
                            }
                            IconButton(onClick = { onMoveSelected(1) }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down")
                            }
                            IconButton(onClick = onDuplicateSelected, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate")
                            }
                            IconButton(onClick = onDeleteSelected, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Screen 11: Multi-Screen Manager
 */
@Composable
fun ScreenManagerScreen(
    document: CanonicalApplicationDocument?,
    onAddScreen: (String) -> Unit,
    onRenameScreen: (String, String) -> Unit,
    onDuplicateScreen: (String) -> Unit,
    onDeleteScreen: (String) -> Unit,
    onSetInitialScreen: (String) -> Unit,
    onOpenScreenInEditor: (String) -> Unit,
    onBack: () -> Unit
) {
    var newScreenName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<CanonicalScreen?>(null) }
    var renameText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            WorkstationTopBar(
                title = "Multi-Screen Manager",
                subtitle = "Create, set entry route, duplicate & organize screens",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        if (document == null) return@Scaffold
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("screen_manager_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newScreenName,
                        onValueChange = { newScreenName = it },
                        label = { Text("New Screen Name") },
                        placeholder = { Text("e.g. Profile Settings") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("new_screen_name_input")
                    )
                    Button(
                        onClick = {
                            onAddScreen(newScreenName)
                            newScreenName = ""
                        },
                        modifier = Modifier.height(52.dp).testTag("add_screen_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Add")
                    }
                }
            }

            items(document.screens, key = { it.id }) { scr ->
                val isHome = scr.id == document.initialScreenId
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(
                        width = if (isHome) 2.dp else 1.dp,
                        color = if (isHome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier.fillMaxWidth().clickable { onOpenScreenInEditor(scr.id) }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(if (isHome) "★ ${scr.name} (Initial)" else scr.name, style = MaterialTheme.typography.titleMedium)
                                Text("Route: ${scr.route} • ${scr.components.size} widgets", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row {
                                if (!isHome) {
                                    IconButton(onClick = { onSetInitialScreen(scr.id) }, modifier = Modifier.size(48.dp)) {
                                        Icon(Icons.Filled.Star, contentDescription = "Set as initial screen")
                                    }
                                }
                                IconButton(onClick = { onDuplicateScreen(scr.id) }, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate screen")
                                }
                                if (document.screens.size > 1) {
                                    IconButton(onClick = { onDeleteScreen(scr.id) }, modifier = Modifier.size(48.dp)) {
                                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete screen", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                        TextButton(onClick = {
                            renameTarget = scr
                            renameText = scr.name
                        }) {
                            Text("Rename Screen")
                        }
                    }
                }
            }
        }
    }

    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Screen") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Screen Title") }
                )
            },
            confirmButton = {
                Button(onClick = {
                    val target = renameTarget!!
                    renameTarget = null
                    onRenameScreen(target.id, renameText)
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }
}

/**
 * Screen 12: Multi-Screen Navigation Flow Builder
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScreenFlowScreen(
    document: CanonicalApplicationDocument?,
    onUpdateComponent: (screenId: String, componentId: String, (CanonicalComponent) -> CanonicalComponent) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            WorkstationTopBar(
                title = "Screen Navigation Flow",
                subtitle = "Bind buttons to target screens & confirm AI links",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        if (document == null) return@Scaffold
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("screen_flow_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(document.screens, key = { it.id }) { scr ->
                val buttons = scr.components.filter { it.type == ComponentType.BUTTON }
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${scr.name} (${scr.route})", style = MaterialTheme.typography.titleMedium)
                        if (buttons.isEmpty()) {
                            Text("No interactive Button widgets on this screen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            buttons.forEach { btn ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Button: \"${btn.label}\"", style = MaterialTheme.typography.titleSmall)
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            FilterChip(
                                                selected = btn.navActionType == NavActionType.NONE,
                                                onClick = {
                                                    onUpdateComponent(scr.id, btn.id) {
                                                        it.copy(navActionType = NavActionType.NONE, targetScreenId = "")
                                                    }
                                                },
                                                label = { Text("No Nav") }
                                            )
                                            FilterChip(
                                                selected = btn.navActionType == NavActionType.NAVIGATE_BACK,
                                                onClick = {
                                                    onUpdateComponent(scr.id, btn.id) {
                                                        it.copy(navActionType = NavActionType.NAVIGATE_BACK, navConfirmedByUser = true)
                                                    }
                                                },
                                                label = { Text("Pop Back") }
                                            )
                                            document.screens.filter { it.id != scr.id }.forEach { dest ->
                                                FilterChip(
                                                    selected = btn.navActionType == NavActionType.NAVIGATE_TO_SCREEN && btn.targetScreenId == dest.id,
                                                    onClick = {
                                                        onUpdateComponent(scr.id, btn.id) {
                                                            it.copy(
                                                                navActionType = NavActionType.NAVIGATE_TO_SCREEN,
                                                                targetScreenId = dest.id,
                                                                navConfirmedByUser = true
                                                            )
                                                        }
                                                    },
                                                    label = { Text("→ ${dest.name}") }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Screen 13: Canonical Theme & Design System Tokens Editor
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemePropertiesScreen(
    document: CanonicalApplicationDocument?,
    onUpdateTheme: ((CanonicalThemeTokens) -> CanonicalThemeTokens) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            WorkstationTopBar(
                title = "Theme & Design Tokens",
                subtitle = "Colors, corner geometry & Material 3 Flutter theme",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        val theme = document?.theme ?: return@Scaffold
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("theme_properties_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = theme.primaryColorHex,
                            onValueChange = { hex -> onUpdateTheme { it.copy(primaryColorHex = hex) } },
                            label = { Text("Primary Color Hex (#RRGGBB)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = theme.accentColorHex,
                            onValueChange = { hex -> onUpdateTheme { it.copy(accentColorHex = hex) } },
                            label = { Text("Accent / Secondary Color Hex (#RRGGBB)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = theme.backgroundColorHex,
                            onValueChange = { hex -> onUpdateTheme { it.copy(backgroundColorHex = hex) } },
                            label = { Text("Background Surface Hex (#RRGGBB)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = theme.surfaceColorHex,
                            onValueChange = { hex -> onUpdateTheme { it.copy(surfaceColorHex = hex) } },
                            label = { Text("Card / Elevated Surface Hex (#RRGGBB)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text("Font Family: ${theme.fontFamily}", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Inter", "Roboto", "Space Grotesk", "Poppins").forEach { font ->
                                FilterChip(
                                    selected = theme.fontFamily == font,
                                    onClick = { onUpdateTheme { it.copy(fontFamily = font) } },
                                    label = { Text(font) }
                                )
                            }
                        }

                        Text("Corner Radius: ${theme.borderRadiusDp} dp", style = MaterialTheme.typography.titleSmall)
                        Slider(
                            value = theme.borderRadiusDp.toFloat(),
                            onValueChange = { v -> onUpdateTheme { it.copy(borderRadiusDp = v.toInt()) } },
                            valueRange = 4f..28f
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Dark Mode Flutter Theme", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = theme.darkMode,
                                onCheckedChange = { dark -> onUpdateTheme { it.copy(darkMode = dark) } }
                            )
                        }

                        Button(
                            onClick = onSave,
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_theme_button")
                        ) {
                            Text("Save Theme Tokens")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Screen 14: Live Interactive Multi-Screen Application Preview
 */
@Composable
fun InteractivePreviewScreen(
    document: CanonicalApplicationDocument?,
    onBack: () -> Unit
) {
    if (document == null || document.screens.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No screens available to preview.")
        }
        return
    }

    val initialId = document.initialScreenId.takeIf { id -> document.screens.any { it.id == id } }
        ?: document.screens.first().id
    val previewRouteStack = remember(document.projectId) { mutableStateListOf(initialId) }
    val fieldValues = remember { mutableStateMapOf<String, String>() }
    val fieldErrors = remember { mutableStateMapOf<String, String>() }
    var lastToast by remember { mutableStateOf("") }

    // Custom state stack inside Preview: pop internal route stack before exiting screen
    BackHandler(enabled = previewRouteStack.size > 1) {
        previewRouteStack.removeAt(previewRouteStack.lastIndex)
    }

    val currentScreenId = previewRouteStack.lastOrNull() ?: initialId
    val currentScreen = document.screens.firstOrNull { it.id == currentScreenId } ?: document.screens.first()

    Scaffold(
        topBar = {
            WorkstationTopBar(
                title = "Interactive Preview",
                subtitle = "${currentScreen.name} (${currentScreen.route}) • Depth ${previewRouteStack.size}",
                onBack = {
                    if (previewRouteStack.size > 1) {
                        previewRouteStack.removeAt(previewRouteStack.lastIndex)
                    } else {
                        onBack()
                    }
                },
                actions = {
                    TextButton(onClick = {
                        previewRouteStack.clear()
                        previewRouteStack.add(initialId)
                        fieldValues.clear()
                        fieldErrors.clear()
                        lastToast = ""
                    }) {
                        Text("Reset")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("interactive_preview_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (lastToast.isNotBlank()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp)
                    ) {
                        Text(
                            text = lastToast,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }

            item {
                Box(modifier = Modifier.widthIn(max = 420.dp)) {
                    CanonicalPhoneFrameRenderer(
                        screen = currentScreen,
                        themeTokens = document.theme,
                        interactiveMode = true,
                        fieldValues = fieldValues,
                        fieldErrors = fieldErrors,
                        onFieldValueChange = { id, text ->
                            fieldValues[id] = text
                            fieldErrors.remove(id)
                        },
                        onTriggerButton = { btn ->
                            // Validate form fields on current screen first
                            var valid = true
                            currentScreen.components.filter { it.type == ComponentType.TEXT_FIELD }.forEach { tf ->
                                val v = fieldValues[tf.id].orEmpty().trim()
                                when {
                                    tf.formRule == FormValidationRule.EMAIL && (!v.contains("@") || !v.contains(".")) -> {
                                        fieldErrors[tf.id] = "Enter a valid email address"
                                        valid = false
                                    }
                                    tf.formRule == FormValidationRule.MIN_LENGTH_6 && v.length < 6 -> {
                                        fieldErrors[tf.id] = "Minimum 6 characters required"
                                        valid = false
                                    }
                                    (tf.formRequired || tf.formRule == FormValidationRule.NON_EMPTY) && v.isEmpty() -> {
                                        fieldErrors[tf.id] = "Required field"
                                        valid = false
                                    }
                                }
                            }
                            if (!valid) {
                                lastToast = "Form validation blocked navigation — check highlighted fields."
                                return@CanonicalPhoneFrameRenderer
                            }

                            when (btn.navActionType) {
                                NavActionType.NAVIGATE_TO_SCREEN -> {
                                    val dest = document.screens.firstOrNull { it.id == btn.targetScreenId }
                                    if (dest != null && btn.navConfirmedByUser) {
                                        previewRouteStack.add(dest.id)
                                        lastToast = "Navigated to ${dest.route}"
                                    } else {
                                        lastToast = "Button '${btn.label}' tapped (confirm target screen in Flow)."
                                    }
                                }
                                NavActionType.NAVIGATE_BACK -> {
                                    if (previewRouteStack.size > 1) {
                                        previewRouteStack.removeAt(previewRouteStack.lastIndex)
                                        lastToast = "Popped route stack"
                                    } else {
                                        lastToast = "Already at root screen."
                                    }
                                }
                                NavActionType.NONE -> {
                                    lastToast = "Executed '${btn.label}' action."
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
