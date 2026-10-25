package com.example.features.sketch_import

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DesignServices
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.core.schema.CanonicalComponent
import com.example.core.schema.CanonicalScreen
import com.example.core.schema.ComponentType
import com.example.features.figma.FigmaFileInspection
import com.example.features.workspace.AsyncResource
import com.example.ui.components.StatusFeedbackBanners
import com.example.ui.components.WorkstationTopBar

/**
 * Screen 6: Sketch Capture, Freehand Wireframe Pad & Image Adjustments
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SketchUploadScreen(
    projectId: String,
    processedBitmap: Bitmap?,
    highContrastEnabled: Boolean,
    analysisState: AsyncResource<CanonicalScreen>,
    errorBanner: String,
    onDismissBanner: () -> Unit,
    onSelectPhotoUri: (android.net.Uri) -> Unit,
    onUseBitmap: (Bitmap) -> Unit,
    onRotate90: () -> Unit,
    onToggleHighContrast: () -> Unit,
    onClearSketch: () -> Unit,
    onRunAnalysis: () -> Unit,
    onOpenFigmaImport: () -> Unit,
    onBack: () -> Unit
) {
    var drawPadMode by remember { mutableStateOf(false) }
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var padSize by remember { mutableStateOf(IntSize(540, 960)) }

    // Zero-permission Android Photo Picker per Google Play Policy
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            drawPadMode = false
            onSelectPhotoUri(uri)
        }
    }

    Scaffold(
        topBar = {
            WorkstationTopBar(
                title = "Sketch Import & Wireframe",
                subtitle = "Zero-permission Photo Picker or Freehand Pad",
                onBack = onBack,
                actions = {
                    OutlinedButton(
                        onClick = onOpenFigmaImport,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("switch_to_figma_button")
                    ) {
                        Icon(Icons.Filled.DesignServices, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Figma")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("sketch_upload_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                StatusFeedbackBanners(
                    statusMessage = "",
                    errorMessage = errorBanner,
                    onDismiss = onDismissBanner
                )
            }

            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("pick_sketch_photo_button")
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Pick Photo")
                    }

                    OutlinedButton(
                        onClick = { drawPadMode = !drawPadMode },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("toggle_draw_pad_button")
                    ) {
                        Icon(Icons.Filled.Brush, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (drawPadMode) "Close Pad" else "Draw Wireframe")
                    }

                    OutlinedButton(
                        onClick = {
                            drawPadMode = false
                            onUseBitmap(SketchImageProcessor.createSampleWireframeBitmap())
                        },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("load_sample_wireframe_button")
                    ) {
                        Text("Starter Wireframe")
                    }
                }
            }

            if (drawPadMode) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Freehand Wireframe Canvas — Draw boxes, inputs, and buttons",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(9f / 14f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                    .onSizeChanged { if (it.width > 0 && it.height > 0) padSize = it }
                                    .pointerInput(Unit) {
                                        detectDragGestures(
                                            onDragStart = { offset -> currentStroke = listOf(offset) },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                currentStroke = currentStroke + change.position
                                            },
                                            onDragEnd = {
                                                if (currentStroke.size > 1) {
                                                    strokes.add(currentStroke)
                                                }
                                                currentStroke = emptyList()
                                            }
                                        )
                                    }
                                    .testTag("freehand_sketch_canvas")
                            ) {
                                ComposeCanvas(modifier = Modifier.fillMaxSize()) {
                                    val all = strokes + listOfNotNull(currentStroke.takeIf { it.size > 1 })
                                    for (pts in all) {
                                        val path = Path().apply {
                                            moveTo(pts.first().x, pts.first().y)
                                            pts.drop(1).forEach { lineTo(it.x, it.y) }
                                        }
                                        drawPath(
                                            path = path,
                                            color = Color(0xFF141E28),
                                            style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                        )
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        strokes.clear()
                                        currentStroke = emptyList()
                                    },
                                    modifier = Modifier.weight(1f).height(48.dp)
                                ) {
                                    Text("Clear Ink")
                                }
                                Button(
                                    onClick = {
                                        val w = padSize.width.coerceAtLeast(360)
                                        val h = padSize.height.coerceAtLeast(640)
                                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                        val androidCanvas = Canvas(bmp)
                                        androidCanvas.drawColor(android.graphics.Color.WHITE)
                                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                            color = android.graphics.Color.rgb(20, 30, 40)
                                            style = Paint.Style.STROKE
                                            strokeWidth = 6f
                                            strokeCap = Paint.Cap.ROUND
                                            strokeJoin = Paint.Join.ROUND
                                        }
                                        for (pts in strokes) {
                                            for (i in 0 until pts.lastIndex) {
                                                androidCanvas.drawLine(
                                                    pts[i].x, pts[i].y,
                                                    pts[i + 1].x, pts[i + 1].y,
                                                    paint
                                                )
                                            }
                                        }
                                        drawPadMode = false
                                        onUseBitmap(bmp)
                                    },
                                    enabled = strokes.isNotEmpty(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("capture_pad_sketch_button")
                                ) {
                                    Icon(Icons.Filled.Check, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Use Sketch")
                                }
                            }
                        }
                    }
                }
            }

            if (processedBitmap != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Prepared Sketch Preview", style = MaterialTheme.typography.titleMedium)
                                Row {
                                    IconButton(onClick = onRotate90, modifier = Modifier.size(48.dp).testTag("rotate_sketch_button")) {
                                        Icon(Icons.Filled.RotateRight, contentDescription = "Rotate 90 degrees")
                                    }
                                    IconButton(onClick = onToggleHighContrast, modifier = Modifier.size(48.dp).testTag("contrast_sketch_button")) {
                                        Icon(
                                            Icons.Filled.Contrast,
                                            contentDescription = "Toggle high contrast ink boost",
                                            tint = if (highContrastEnabled) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    IconButton(onClick = onClearSketch, modifier = Modifier.size(48.dp)) {
                                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove sketch", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }

                            Image(
                                bitmap = processedBitmap.asImageBitmap(),
                                contentDescription = "Selected wireframe sketch",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(340.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White)
                            )

                            if (analysisState is AsyncResource.Error) {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = analysisState.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }

                            val analyzing = analysisState is AsyncResource.Loading
                            Button(
                                onClick = onRunAnalysis,
                                enabled = !analyzing,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("run_sketch_ai_analysis_button")
                            ) {
                                if (analyzing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text("Gemini 3.1 Pro Analyzing Wireframe...")
                                } else {
                                    Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Analyze Sketch with Gemini 3.1 Pro")
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
 * Screen 7: Figma Design Connection & Multi-Frame Import
 */
@Composable
fun FigmaImportScreen(
    projectId: String,
    inspectionState: AsyncResource<FigmaFileInspection>,
    onInspectFile: (String) -> Unit,
    onImportFrames: (selectedNodeIds: Set<String>, replaceExisting: Boolean) -> Unit,
    onBack: () -> Unit
) {
    var figmaUrlOrKey by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var replaceExisting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            WorkstationTopBar(
                title = "Figma Design Import",
                subtitle = "Inspect top-level frames via Figma REST API v1",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("figma_import_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Connect Authorized Figma File", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Paste a Figma design URL (https://www.figma.com/design/<KEY>/...) or file key. Ensure FIGMA_ACCESS_TOKEN is configured in AI Studio Secrets.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = figmaUrlOrKey,
                            onValueChange = { figmaUrlOrKey = it },
                            label = { Text("Figma File URL or Key") },
                            placeholder = { Text("https://www.figma.com/design/AbCdEf123456/App") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("figma_url_input")
                        )
                        Button(
                            onClick = { onInspectFile(figmaUrlOrKey) },
                            enabled = figmaUrlOrKey.isNotBlank() && inspectionState !is AsyncResource.Loading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("inspect_figma_button")
                        ) {
                            if (inspectionState is AsyncResource.Loading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Fetching Figma Frames...")
                            } else {
                                Text("Fetch Frames")
                            }
                        }
                    }
                }
            }

            when (inspectionState) {
                is AsyncResource.Error -> {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = inspectionState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }

                is AsyncResource.Success -> {
                    val info = inspectionState.data
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(info.fileName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${info.frames.size} frame(s) discovered • Last modified ${info.lastModified}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    selectedIds = if (selectedIds.size == info.frames.size) emptySet()
                                    else info.frames.map { it.nodeId }.toSet()
                                }
                            ) {
                                Text(if (selectedIds.size == info.frames.size) "Clear" else "Select All")
                            }
                        }
                    }

                    items(info.frames, key = { it.nodeId }) { frame ->
                        val checked = frame.nodeId in selectedIds
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            border = BorderStroke(
                                width = if (checked) 2.dp else 1.dp,
                                color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedIds = if (checked) selectedIds - frame.nodeId else selectedIds + frame.nodeId
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { isChecked ->
                                        selectedIds = if (isChecked) selectedIds + frame.nodeId else selectedIds - frame.nodeId
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(frame.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${frame.pageName} • ${frame.widthPx}×${frame.heightPx}px • ${frame.childCount} nodes",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Replace existing screens in project", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = replaceExisting, onCheckedChange = { replaceExisting = it })
                        }
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { onImportFrames(selectedIds, replaceExisting) },
                            enabled = selectedIds.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("import_figma_frames_button")
                        ) {
                            Text("Import ${selectedIds.size} Selected Frame(s)")
                        }
                    }
                }

                else -> {}
            }
        }
    }
}

/**
 * Screen 8: AI Design Analysis Review & Element Confirmation
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiAnalysisReviewScreen(
    sketchBitmap: Bitmap?,
    analysisState: AsyncResource<CanonicalScreen>,
    onUpdateComponent: (String, (CanonicalComponent) -> CanonicalComponent) -> Unit,
    onRemoveComponent: (String) -> Unit,
    onAcceptScreen: (replaceExisting: Boolean) -> Unit,
    onBack: () -> Unit
) {
    var replaceExisting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            WorkstationTopBar(
                title = "AI Analysis Review",
                subtitle = "Inspect confidence, adjust widget types & confirm routes",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        val reviewed = (analysisState as? AsyncResource.Success)?.data
        if (reviewed == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("No pending AI sketch analysis found. Run Sketch Analysis first.")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("ai_analysis_review_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (sketchBitmap != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                bitmap = sketchBitmap.asImageBitmap(),
                                contentDescription = "Analyzed sketch thumbnail",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(72.dp, 108.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Screen: ${reviewed.name}", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${reviewed.components.size} UI components extracted • Route ${reviewed.route}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (reviewed.warnings.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = reviewed.warnings.joinToString(" • "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            items(reviewed.components, key = { it.id }) { comp ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${comp.type.displayLabel} • ${(comp.confidence * 100).toInt()}% confidence",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (comp.confidence < 0.72) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.primary
                            )
                            IconButton(
                                onClick = { onRemoveComponent(comp.id) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    Icons.Filled.DeleteOutline,
                                    contentDescription = "Delete detected element",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        OutlinedTextField(
                            value = comp.label,
                            onValueChange = { txt -> onUpdateComponent(comp.id) { it.copy(label = txt) } },
                            label = { Text("Component Label") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                ComponentType.APP_BAR,
                                ComponentType.TEXT,
                                ComponentType.TEXT_FIELD,
                                ComponentType.BUTTON,
                                ComponentType.CARD,
                                ComponentType.IMAGE
                            ).forEach { targetType ->
                                FilterChip(
                                    selected = comp.type == targetType,
                                    onClick = { onUpdateComponent(comp.id) { it.copy(type = targetType) } },
                                    label = { Text(targetType.displayLabel) }
                                )
                            }
                        }

                        if (comp.navSuggestedByAi) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Confirm AI navigation action (${comp.navActionType.name})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(
                                    checked = comp.navConfirmedByUser,
                                    onCheckedChange = { confirmed ->
                                        onUpdateComponent(comp.id) { it.copy(navConfirmedByUser = confirmed) }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Replace existing project screens", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = replaceExisting, onCheckedChange = { replaceExisting = it })
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { onAcceptScreen(replaceExisting) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("accept_ai_review_button")
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Accept into Visual Editor")
                }
            }
        }
    }
}
