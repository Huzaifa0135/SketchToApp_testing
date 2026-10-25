package com.example.features.workspace

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.SketchToAppRepository
import com.example.core.models.BuildJobRecord
import com.example.core.models.GeneratedAppRecord
import com.example.core.models.ProjectRecord
import com.example.core.models.UsageLogRecord
import com.example.core.models.UserProfile
import com.example.core.schema.CanonicalApplicationDocument
import com.example.core.schema.CanonicalComponent
import com.example.core.schema.CanonicalScreen
import com.example.core.schema.CanonicalThemeTokens
import com.example.core.schema.ComponentType
import com.example.core.schema.SchemaDiagnostic
import com.example.core.schema.SchemaValidator
import com.example.features.ai.AiSketchAnalysisPayload
import com.example.features.ai.GeminiAgentsService
import com.example.features.code_generation.ExportedArchiveResult
import com.example.features.code_generation.FlutterProjectGenerator
import com.example.features.code_generation.GeneratedFlutterProject
import com.example.features.code_generation.ZipAndArtifactExporter
import com.example.features.figma.FigmaFileInspection
import com.example.features.figma.FigmaImportService
import com.example.features.sketch_import.SketchImageProcessor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface AsyncResource<out T> {
    data object Idle : AsyncResource<Nothing>
    data object Loading : AsyncResource<Nothing>
    data class Success<T>(val data: T) : AsyncResource<T>
    data class Error(val message: String) : AsyncResource<Nothing>
}

data class ActiveEditorState(
    val document: CanonicalApplicationDocument? = null,
    val selectedScreenId: String = "",
    val selectedComponentId: String = "",
    val undoStack: List<CanonicalApplicationDocument> = emptyList(),
    val redoStack: List<CanonicalApplicationDocument> = emptyList(),
    val isSaving: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val statusBanner: String = "",
    val errorBanner: String = ""
)

class SketchWorkspaceViewModel(
    private val repository: SketchToAppRepository,
    val currentUserId: String,
    initialDisplayName: String,
    initialEmail: String?,
    initialPhotoUrl: String?
) : ViewModel() {

    val userProfileState: StateFlow<UserProfile?> = repository.observeUserProfile(currentUserId)
        .catch { e ->
            Log.w(TAG, "Error observing user profile", e)
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val projectsState: StateFlow<AsyncResource<List<ProjectRecord>>> = repository.observeProjects(currentUserId)
        .map<List<ProjectRecord>, AsyncResource<List<ProjectRecord>>> { AsyncResource.Success(it) }
        .catch { e ->
            Log.w(TAG, "Error observing projects", e)
            emit(AsyncResource.Error(e.localizedMessage ?: "Failed to observe projects"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AsyncResource.Loading)

    val usageLogsState: StateFlow<List<UsageLogRecord>> = repository.observeUsageLogs(currentUserId)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _activeProjectId = MutableStateFlow("")
    val activeProjectId: StateFlow<String> = _activeProjectId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val buildJobsState: StateFlow<List<BuildJobRecord>> = _activeProjectId
        .flatMapLatest { pid ->
            if (pid.isBlank()) flowOf(emptyList())
            else repository.observeBuildJobs(currentUserId, pid).catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val generatedAppsState: StateFlow<List<GeneratedAppRecord>> = _activeProjectId
        .flatMapLatest { pid ->
            if (pid.isBlank()) flowOf(emptyList())
            else repository.observeGeneratedApps(currentUserId, pid).catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _editorState = MutableStateFlow(ActiveEditorState())
    val editorState: StateFlow<ActiveEditorState> = _editorState.asStateFlow()

    // Sketch import & review state
    private val _rawSketchBitmap = MutableStateFlow<Bitmap?>(null)
    private val _sketchRotation = MutableStateFlow(0)
    private val _sketchHighContrast = MutableStateFlow(false)
    private val _processedSketchBitmap = MutableStateFlow<Bitmap?>(null)
    val processedSketchBitmap: StateFlow<Bitmap?> = _processedSketchBitmap.asStateFlow()
    val sketchHighContrast: StateFlow<Boolean> = _sketchHighContrast.asStateFlow()

    private val _sketchAnalysisState = MutableStateFlow<AsyncResource<CanonicalScreen>>(AsyncResource.Idle)
    val sketchAnalysisState: StateFlow<AsyncResource<CanonicalScreen>> = _sketchAnalysisState.asStateFlow()

    // Figma import state
    private val _figmaInspectionState = MutableStateFlow<AsyncResource<FigmaFileInspection>>(AsyncResource.Idle)
    val figmaInspectionState: StateFlow<AsyncResource<FigmaFileInspection>> = _figmaInspectionState.asStateFlow()

    // Generated Flutter project & exported ZIP state
    private val _generatedProject = MutableStateFlow<GeneratedFlutterProject?>(null)
    val generatedProject: StateFlow<GeneratedFlutterProject?> = _generatedProject.asStateFlow()

    private val _lastExportedZip = MutableStateFlow<ExportedArchiveResult?>(null)
    val lastExportedZip: StateFlow<ExportedArchiveResult?> = _lastExportedZip.asStateFlow()

    private val _lastGenerationJobId = MutableStateFlow("")
    val lastGenerationJobId: StateFlow<String> = _lastGenerationJobId.asStateFlow()

    private val _isBusySubmitting = MutableStateFlow(false)
    val isBusySubmitting: StateFlow<Boolean> = _isBusySubmitting.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Int?>(null)
    val downloadProgress: StateFlow<Int?> = _downloadProgress.asStateFlow()

    private val _assistantResponse = MutableStateFlow<AsyncResource<String>>(AsyncResource.Idle)
    val assistantResponse: StateFlow<AsyncResource<String>> = _assistantResponse.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureUserProfile(initialDisplayName, initialEmail, initialPhotoUrl)
        }
    }

    fun clearBanners() {
        _editorState.update { it.copy(statusBanner = "", errorBanner = "") }
    }

    fun markOnboardingComplete() {
        val currentName = userProfileState.value?.displayName ?: "Developer"
        viewModelScope.launch {
            repository.updateProfilePreferences(currentName, onboardingCompleted = true)
        }
    }

    fun updateAccountDisplayName(newName: String) {
        viewModelScope.launch {
            val res = repository.updateProfilePreferences(newName, onboardingCompleted = true)
            if (res.isSuccess) {
                _editorState.update { it.copy(statusBanner = "Profile updated.") }
            } else {
                _editorState.update { it.copy(errorBanner = res.exceptionOrNull()?.localizedMessage ?: "Failed to update profile.") }
            }
        }
    }

    // --- Project Lifecycle ---

    fun createNewProject(
        name: String,
        description: String,
        packageName: String,
        sourceType: String,
        onCreated: (String) -> Unit
    ) {
        if (_isBusySubmitting.value) return
        _isBusySubmitting.value = true
        viewModelScope.launch {
            val result = repository.createProject(name, description, packageName, sourceType)
            _isBusySubmitting.value = false
            result.onSuccess { pid ->
                openProject(pid)
                onCreated(pid)
            }.onFailure { err ->
                _editorState.update { it.copy(errorBanner = err.localizedMessage ?: "Project creation failed.") }
            }
        }
    }

    fun openProject(projectId: String) {
        if (projectId.isBlank()) return
        _activeProjectId.value = projectId
        viewModelScope.launch {
            val res = repository.loadCanonicalDocument(projectId)
            res.onSuccess { doc ->
                val selScreen = doc.initialScreenId.ifBlank { doc.screens.firstOrNull()?.id.orEmpty() }
                val selComp = doc.screens.firstOrNull { it.id == selScreen }?.components?.firstOrNull()?.id.orEmpty()
                _editorState.value = ActiveEditorState(
                    document = doc,
                    selectedScreenId = selScreen,
                    selectedComponentId = selComp
                )
                _generatedProject.value = GeminiAgentsService.generateAndAuditFlutterProject(doc)
            }.onFailure { err ->
                _editorState.update { it.copy(errorBanner = err.localizedMessage ?: "Failed to load project.") }
            }
        }
    }

    fun duplicateProject(projectId: String) {
        if (_isBusySubmitting.value) return
        _isBusySubmitting.value = true
        viewModelScope.launch {
            val res = repository.duplicateProject(projectId)
            _isBusySubmitting.value = false
            res.onSuccess { newId ->
                openProject(newId)
                _editorState.update { it.copy(statusBanner = "Project duplicated.") }
            }.onFailure { err ->
                _editorState.update { it.copy(errorBanner = err.localizedMessage ?: "Failed to duplicate project.") }
            }
        }
    }

    fun deleteProject(projectId: String, onDeleted: () -> Unit = {}) {
        if (_isBusySubmitting.value) return
        _isBusySubmitting.value = true
        viewModelScope.launch {
            val res = repository.deleteProjectCascade(projectId)
            _isBusySubmitting.value = false
            res.onSuccess {
                if (_activeProjectId.value == projectId) {
                    _activeProjectId.value = ""
                    _editorState.value = ActiveEditorState()
                }
                onDeleted()
            }.onFailure { err ->
                _editorState.update { it.copy(errorBanner = err.localizedMessage ?: "Failed to delete project.") }
            }
        }
    }

    // --- Sketch Upload, Adjustments & AI Design Analysis ---

    fun setSketchFromBitmap(bitmap: Bitmap) {
        _rawSketchBitmap.value = bitmap
        _sketchRotation.value = 0
        _sketchHighContrast.value = false
        _processedSketchBitmap.value = bitmap
        _sketchAnalysisState.value = AsyncResource.Idle
    }

    fun loadSketchFromUri(context: Context, uri: Uri, projectId: String) {
        viewModelScope.launch {
            val res = SketchImageProcessor.loadAndValidateFromUri(context, uri)
            res.onSuccess { loaded ->
                setSketchFromBitmap(loaded.bitmap)
                repository.registerAsset(
                    projectId = projectId,
                    name = "sketch_import.jpg",
                    mimeType = loaded.mimeType,
                    sizeBytes = loaded.sizeBytes,
                    storageRef = loaded.cachedFilePath,
                    checksumSha256 = loaded.sha256Hex
                )
            }.onFailure { err ->
                _editorState.update { it.copy(errorBanner = err.localizedMessage ?: "Invalid sketch image.") }
            }
        }
    }

    fun rotateSketch90() {
        val raw = _rawSketchBitmap.value ?: return
        val nextRot = (_sketchRotation.value + 90) % 360
        _sketchRotation.value = nextRot
        _processedSketchBitmap.value = SketchImageProcessor.applyTransformations(
            raw,
            nextRot,
            _sketchHighContrast.value
        )
    }

    fun toggleSketchHighContrast() {
        val raw = _rawSketchBitmap.value ?: return
        val next = !_sketchHighContrast.value
        _sketchHighContrast.value = next
        _processedSketchBitmap.value = SketchImageProcessor.applyTransformations(
            raw,
            _sketchRotation.value,
            next
        )
    }

    fun clearSketchSelection() {
        _rawSketchBitmap.value = null
        _processedSketchBitmap.value = null
        _sketchAnalysisState.value = AsyncResource.Idle
    }

    fun runSketchAiAnalysis(projectId: String, onReadyForReview: () -> Unit) {
        val bmp = _processedSketchBitmap.value ?: return
        if (_sketchAnalysisState.value is AsyncResource.Loading) return

        val profile = userProfileState.value
        if (profile != null && profile.planTier == "free" && profile.aiCallsUsed >= SketchToAppRepository.FREE_PLAN_AI_LIMIT) {
            _sketchAnalysisState.value = AsyncResource.Error(
                "Free tier AI analysis quota reached (${SketchToAppRepository.FREE_PLAN_AI_LIMIT} calls). Check Usage & Quotas."
            )
            return
        }

        _sketchAnalysisState.value = AsyncResource.Loading
        viewModelScope.launch {
            val currentDoc = _editorState.value.document
            val result = GeminiAgentsService.analyzeSketchImage(
                bitmap = bmp,
                projectContextNote = currentDoc?.appName.orEmpty()
            )
            result.onSuccess { payload: AiSketchAnalysisPayload ->
                val mappedScreen = GeminiAgentsService.mapDetectedElementsToScreen(
                    payload = payload,
                    existingScreens = currentDoc?.screens.orEmpty()
                )
                repository.saveDesignRecord(
                    projectId = projectId,
                    sourceType = "sketch",
                    sourceReference = "local://cache/sketch",
                    analysisStatus = "completed",
                    warningCount = payload.warnings.size,
                    warningsSummary = payload.warnings.joinToString("; ")
                )
                repository.recordUsageAndIncrementQuota(
                    actionType = "sketch_analysis",
                    projectId = projectId,
                    status = "succeeded",
                    incrementAiCalls = true
                )
                repository.renameOrUpdateProject(projectId = projectId, stage = "analyzed", sourceType = "sketch")
                _sketchAnalysisState.value = AsyncResource.Success(mappedScreen)
                onReadyForReview()
            }.onFailure { err ->
                repository.recordUsageAndIncrementQuota(
                    actionType = "sketch_analysis",
                    projectId = projectId,
                    status = "failed"
                )
                _sketchAnalysisState.value = AsyncResource.Error(
                    err.localizedMessage ?: "Sketch AI analysis failed."
                )
            }
        }
    }

    fun updatePendingReviewedComponent(componentId: String, transform: (CanonicalComponent) -> CanonicalComponent) {
        val current = (_sketchAnalysisState.value as? AsyncResource.Success)?.data ?: return
        val updated = current.copy(
            components = current.components.map { if (it.id == componentId) transform(it) else it }
        )
        _sketchAnalysisState.value = AsyncResource.Success(updated)
    }

    fun removePendingReviewedComponent(componentId: String) {
        val current = (_sketchAnalysisState.value as? AsyncResource.Success)?.data ?: return
        _sketchAnalysisState.value = AsyncResource.Success(
            current.copy(components = current.components.filterNot { it.id == componentId })
        )
    }

    fun acceptReviewedSketchScreen(replaceExistingScreens: Boolean, onAccepted: () -> Unit) {
        val reviewedScreen = (_sketchAnalysisState.value as? AsyncResource.Success)?.data ?: return
        val doc = _editorState.value.document ?: return

        val updatedScreens = if (replaceExistingScreens || doc.screens.isEmpty()) {
            listOf(reviewedScreen.copy(isInitial = true, orderIndex = 0))
        } else {
            doc.screens + reviewedScreen.copy(isInitial = false, orderIndex = doc.screens.size)
        }
        val initialId = if (replaceExistingScreens || doc.screens.isEmpty()) reviewedScreen.id else doc.initialScreenId
        val nextDoc = doc.copy(
            initialScreenId = initialId,
            screens = updatedScreens
        )
        applyDocumentMutation(nextDoc, selectScreenId = reviewedScreen.id)
        saveActiveDocument(onSaved = onAccepted)
    }

    // --- Figma Connection & Import ---

    fun inspectFigmaFile(urlOrKey: String) {
        if (_figmaInspectionState.value is AsyncResource.Loading) return
        _figmaInspectionState.value = AsyncResource.Loading
        viewModelScope.launch {
            val res = FigmaImportService.fetchFigmaFrames(urlOrKey)
            _figmaInspectionState.value = res.fold(
                onSuccess = { AsyncResource.Success(it) },
                onFailure = { AsyncResource.Error(it.localizedMessage ?: "Figma connection failed.") }
            )
        }
    }

    fun importSelectedFigmaFrames(
        projectId: String,
        selectedNodeIds: Set<String>,
        replaceExisting: Boolean,
        onImported: () -> Unit
    ) {
        val inspection = (_figmaInspectionState.value as? AsyncResource.Success)?.data ?: return
        val doc = _editorState.value.document ?: return
        val chosen = inspection.frames.filter { it.nodeId in selectedNodeIds }
        if (chosen.isEmpty()) return

        val baseIndex = if (replaceExisting) 0 else doc.screens.size
        val mappedScreens = chosen.mapIndexed { idx, frame ->
            FigmaImportService.mapFrameToCanonicalScreen(
                frame = frame,
                orderIndex = baseIndex + idx,
                isInitial = replaceExisting && idx == 0
            )
        }

        val combinedScreens = if (replaceExisting) mappedScreens else (doc.screens + mappedScreens)
        val initialId = if (replaceExisting) mappedScreens.first().id else doc.initialScreenId
        val nextDoc = doc.copy(
            initialScreenId = initialId,
            screens = combinedScreens
        )
        applyDocumentMutation(nextDoc, selectScreenId = mappedScreens.first().id)

        viewModelScope.launch {
            val allWarnings = mappedScreens.flatMap { it.warnings }
            repository.saveDesignRecord(
                projectId = projectId,
                sourceType = "figma",
                sourceReference = "figma://${inspection.fileKey}",
                analysisStatus = "accepted",
                warningCount = allWarnings.size,
                warningsSummary = allWarnings.joinToString("; ")
            )
            repository.recordUsageAndIncrementQuota(
                actionType = "figma_import",
                projectId = projectId,
                status = "succeeded"
            )
            repository.renameOrUpdateProject(projectId = projectId, sourceType = "figma")
            saveActiveDocument(onSaved = onImported)
        }
    }

    // --- Visual Editor, Multi-Screen Builder, Theme & Undo/Redo ---

    private fun applyDocumentMutation(
        nextDoc: CanonicalApplicationDocument,
        selectScreenId: String? = null,
        selectComponentId: String? = null
    ) {
        _editorState.update { current ->
            val prevDoc = current.document
            val nextUndo = if (prevDoc != null) (current.undoStack + prevDoc).takeLast(25) else current.undoStack
            val resolvedScreen = selectScreenId
                ?: current.selectedScreenId.takeIf { id -> nextDoc.screens.any { it.id == id } }
                ?: nextDoc.initialScreenId
            val screenObj = nextDoc.screens.firstOrNull { it.id == resolvedScreen }
            val resolvedComp = selectComponentId
                ?: current.selectedComponentId.takeIf { cid -> screenObj?.components?.any { it.id == cid } == true }
                ?: screenObj?.components?.firstOrNull()?.id.orEmpty()

            current.copy(
                document = nextDoc,
                selectedScreenId = resolvedScreen,
                selectedComponentId = resolvedComp,
                undoStack = nextUndo,
                redoStack = emptyList(),
                hasUnsavedChanges = true,
                errorBanner = ""
            )
        }
        _generatedProject.value = GeminiAgentsService.generateAndAuditFlutterProject(nextDoc)
    }

    fun undoEdit() {
        _editorState.update { state ->
            val prev = state.undoStack.lastOrNull() ?: return@update state
            val cur = state.document
            state.copy(
                document = prev,
                undoStack = state.undoStack.dropLast(1),
                redoStack = if (cur != null) state.redoStack + cur else state.redoStack,
                hasUnsavedChanges = true
            )
        }
        _editorState.value.document?.let {
            _generatedProject.value = GeminiAgentsService.generateAndAuditFlutterProject(it)
        }
    }

    fun redoEdit() {
        _editorState.update { state ->
            val next = state.redoStack.lastOrNull() ?: return@update state
            val cur = state.document
            state.copy(
                document = next,
                undoStack = if (cur != null) state.undoStack + cur else state.undoStack,
                redoStack = state.redoStack.dropLast(1),
                hasUnsavedChanges = true
            )
        }
        _editorState.value.document?.let {
            _generatedProject.value = GeminiAgentsService.generateAndAuditFlutterProject(it)
        }
    }

    fun selectScreen(screenId: String) {
        _editorState.update { state ->
            val scr = state.document?.screens?.firstOrNull { it.id == screenId }
            state.copy(
                selectedScreenId = screenId,
                selectedComponentId = scr?.components?.firstOrNull()?.id.orEmpty()
            )
        }
    }

    fun selectComponent(componentId: String) {
        _editorState.update { it.copy(selectedComponentId = componentId) }
    }

    fun addComponentToSelectedScreen(type: ComponentType) {
        val state = _editorState.value
        val doc = state.document ?: return
        val newComp = CanonicalComponent(
            id = "cmp_" + UUID.randomUUID().toString().replace("-", "").take(10),
            type = type,
            label = when (type) {
                ComponentType.APP_BAR -> "Screen Header"
                ComponentType.TEXT -> "Heading or Body Text"
                ComponentType.BUTTON -> "Primary Action"
                ComponentType.TEXT_FIELD -> "Input Field"
                ComponentType.CARD -> "Feature Card"
                ComponentType.IMAGE -> "Banner Image"
                else -> type.displayLabel
            },
            hint = if (type == ComponentType.TEXT_FIELD) "Type value..." else "",
            backgroundColorHex = if (type == ComponentType.BUTTON) doc.theme.primaryColorHex else ""
        )
        val nextScreens = doc.screens.map { s ->
            if (s.id == state.selectedScreenId) s.copy(components = s.components + newComp) else s
        }
        applyDocumentMutation(doc.copy(screens = nextScreens), selectComponentId = newComp.id)
    }

    fun updateSelectedComponent(transform: (CanonicalComponent) -> CanonicalComponent) {
        val state = _editorState.value
        val doc = state.document ?: return
        val nextScreens = doc.screens.map { s ->
            if (s.id == state.selectedScreenId) {
                s.copy(
                    components = s.components.map { c ->
                        if (c.id == state.selectedComponentId) transform(c) else c
                    }
                )
            } else s
        }
        applyDocumentMutation(doc.copy(screens = nextScreens))
    }

    fun updateComponentById(screenId: String, componentId: String, transform: (CanonicalComponent) -> CanonicalComponent) {
        val doc = _editorState.value.document ?: return
        val nextScreens = doc.screens.map { s ->
            if (s.id == screenId) {
                s.copy(components = s.components.map { c -> if (c.id == componentId) transform(c) else c })
            } else s
        }
        applyDocumentMutation(doc.copy(screens = nextScreens))
    }

    fun duplicateSelectedComponent() {
        val state = _editorState.value
        val doc = state.document ?: return
        val screen = doc.screens.firstOrNull { it.id == state.selectedScreenId } ?: return
        val target = screen.components.firstOrNull { it.id == state.selectedComponentId } ?: return
        val copy = target.copy(
            id = "cmp_" + UUID.randomUUID().toString().replace("-", "").take(10),
            label = "${target.label} Copy".take(120)
        )
        val nextScreens = doc.screens.map { s ->
            if (s.id == screen.id) s.copy(components = s.components + copy) else s
        }
        applyDocumentMutation(doc.copy(screens = nextScreens), selectComponentId = copy.id)
    }

    fun deleteSelectedComponent() {
        val state = _editorState.value
        val doc = state.document ?: return
        val nextScreens = doc.screens.map { s ->
            if (s.id == state.selectedScreenId) {
                s.copy(components = s.components.filterNot { it.id == state.selectedComponentId })
            } else s
        }
        applyDocumentMutation(doc.copy(screens = nextScreens), selectComponentId = "")
    }

    fun moveSelectedComponent(delta: Int) {
        val state = _editorState.value
        val doc = state.document ?: return
        val screen = doc.screens.firstOrNull { it.id == state.selectedScreenId } ?: return
        val idx = screen.components.indexOfFirst { it.id == state.selectedComponentId }
        if (idx == -1) return
        val targetIdx = (idx + delta).coerceIn(0, screen.components.lastIndex)
        if (targetIdx == idx) return

        val mutable = screen.components.toMutableList()
        val item = mutable.removeAt(idx)
        mutable.add(targetIdx, item)
        val nextScreens = doc.screens.map { s ->
            if (s.id == screen.id) s.copy(components = mutable) else s
        }
        applyDocumentMutation(doc.copy(screens = nextScreens))
    }

    // --- Screen Management ---

    fun addScreen(name: String) {
        val doc = _editorState.value.document ?: return
        val clean = name.trim().ifBlank { "Screen ${doc.screens.size + 1}" }.take(50)
        val slug = FlutterProjectGenerator.sanitizeSlug(clean)
        val newScreen = CanonicalScreen(
            id = "scr_" + UUID.randomUUID().toString().replace("-", "").take(12),
            name = clean,
            route = "/${slug}_${doc.screens.size + 1}",
            isInitial = doc.screens.isEmpty(),
            orderIndex = doc.screens.size,
            components = listOf(
                CanonicalComponent(
                    id = "cmp_" + UUID.randomUUID().toString().replace("-", "").take(8),
                    type = ComponentType.APP_BAR,
                    label = clean
                ),
                CanonicalComponent(
                    id = "cmp_" + UUID.randomUUID().toString().replace("-", "").take(8),
                    type = ComponentType.TEXT,
                    label = clean,
                    fontSizeSp = 20,
                    fontWeight = "bold"
                ),
                CanonicalComponent(
                    id = "cmp_" + UUID.randomUUID().toString().replace("-", "").take(8),
                    type = ComponentType.BUTTON,
                    label = "Back",
                    navActionType = com.example.core.schema.NavActionType.NAVIGATE_BACK
                )
            )
        )
        applyDocumentMutation(
            doc.copy(
                initialScreenId = doc.initialScreenId.ifBlank { newScreen.id },
                screens = doc.screens + newScreen
            ),
            selectScreenId = newScreen.id
        )
    }

    fun renameScreen(screenId: String, newName: String) {
        val doc = _editorState.value.document ?: return
        val clean = newName.trim().ifBlank { return }.take(50)
        val slug = FlutterProjectGenerator.sanitizeSlug(clean)
        val nextScreens = doc.screens.mapIndexed { idx, s ->
            if (s.id == screenId) s.copy(name = clean, route = "/${slug}_${idx + 1}") else s
        }
        applyDocumentMutation(doc.copy(screens = nextScreens))
    }

    fun duplicateScreen(screenId: String) {
        val doc = _editorState.value.document ?: return
        val src = doc.screens.firstOrNull { it.id == screenId } ?: return
        val copyName = "${src.name.take(40)} Copy"
        val slug = FlutterProjectGenerator.sanitizeSlug(copyName)
        val copy = src.copy(
            id = "scr_" + UUID.randomUUID().toString().replace("-", "").take(12),
            name = copyName,
            route = "/${slug}_${doc.screens.size + 1}",
            isInitial = false,
            orderIndex = doc.screens.size,
            components = src.components.map { c ->
                c.copy(id = "cmp_" + UUID.randomUUID().toString().replace("-", "").take(10))
            }
        )
        applyDocumentMutation(doc.copy(screens = doc.screens + copy), selectScreenId = copy.id)
    }

    fun deleteScreen(screenId: String) {
        val doc = _editorState.value.document ?: return
        if (doc.screens.size <= 1) {
            _editorState.update { it.copy(errorBanner = "A project must retain at least one screen.") }
            return
        }
        val remaining = doc.screens.filterNot { it.id == screenId }
            .mapIndexed { idx, s -> s.copy(orderIndex = idx) }
        val newInitial = if (doc.initialScreenId == screenId) remaining.first().id else doc.initialScreenId
        applyDocumentMutation(
            doc.copy(
                initialScreenId = newInitial,
                screens = remaining.map { it.copy(isInitial = it.id == newInitial) }
            ),
            selectScreenId = remaining.first().id
        )
    }

    fun setInitialScreen(screenId: String) {
        val doc = _editorState.value.document ?: return
        val nextScreens = doc.screens.map { it.copy(isInitial = it.id == screenId) }
        applyDocumentMutation(doc.copy(initialScreenId = screenId, screens = nextScreens))
    }

    fun updateThemeAndAppMetadata(
        appName: String? = null,
        packageName: String? = null,
        themeTransform: ((CanonicalThemeTokens) -> CanonicalThemeTokens)? = null
    ) {
        val doc = _editorState.value.document ?: return
        val nextDoc = doc.copy(
            appName = appName?.take(80) ?: doc.appName,
            packageName = packageName?.take(120) ?: doc.packageName,
            theme = themeTransform?.invoke(doc.theme) ?: doc.theme
        )
        applyDocumentMutation(nextDoc)
    }

    fun saveActiveDocument(onSaved: () -> Unit = {}) {
        val state = _editorState.value
        val doc = state.document ?: return
        if (state.isSaving) return

        val validation = SchemaValidator.validate(doc)
        if (!validation.isValidForGeneration) {
            val firstErr = validation.errors.firstOrNull()?.message ?: "Validation error"
            _editorState.update { it.copy(errorBanner = "Cannot save invalid schema: $firstErr") }
            return
        }

        _editorState.update { it.copy(isSaving = true, errorBanner = "") }
        viewModelScope.launch {
            val res = repository.saveCanonicalDocument(doc)
            res.onSuccess {
                _editorState.update {
                    it.copy(
                        isSaving = false,
                        hasUnsavedChanges = false,
                        statusBanner = "Saved canonical document to Cloud Firestore."
                    )
                }
                onSaved()
            }.onFailure { err ->
                _editorState.update {
                    it.copy(
                        isSaving = false,
                        errorBanner = err.localizedMessage ?: "Failed to save document."
                    )
                }
            }
        }
    }

    // --- Flutter Generation, ZIP Export & Build Lab ---

    fun runFlutterCodeGenerationAndExportZip(context: Context, onReady: () -> Unit = {}) {
        val doc = _editorState.value.document ?: return
        if (_isBusySubmitting.value) return
        _isBusySubmitting.value = true

        viewModelScope.launch {
            // Persist latest edits first
            repository.saveCanonicalDocument(doc)
            val generated = GeminiAgentsService.generateAndAuditFlutterProject(doc)
            _generatedProject.value = generated

            if (!generated.isValid) {
                val diagText = generated.diagnostics.joinToString("\n") { "[${it.severity}] ${it.code}: ${it.message}" }
                repository.createGenerationJob(
                    projectId = doc.projectId,
                    fileCount = 0,
                    diagnosticsSummary = diagText,
                    zipStorageRef = "",
                    zipChecksumSha256 = "",
                    succeeded = false
                )
                _isBusySubmitting.value = false
                _editorState.update { it.copy(errorBanner = "Code generation halted due to schema errors. Check Diagnostics.") }
                return@launch
            }

            val zipRes = ZipAndArtifactExporter.createFlutterProjectZip(context, generated)
            zipRes.onSuccess { archive ->
                _lastExportedZip.value = archive
                val diagSummary = if (generated.diagnostics.isEmpty()) {
                    "0 errors, 0 warnings — ${generated.files.size} files verified."
                } else {
                    generated.diagnostics.joinToString("; ") { "${it.code}: ${it.message}" }
                }
                val jobRes = repository.createGenerationJob(
                    projectId = doc.projectId,
                    fileCount = generated.files.size,
                    diagnosticsSummary = diagSummary,
                    zipStorageRef = archive.file.absolutePath,
                    zipChecksumSha256 = archive.sha256Hex,
                    succeeded = true
                )
                _lastGenerationJobId.value = jobRes.getOrDefault("")
                repository.recordUsageAndIncrementQuota(
                    actionType = "code_generation",
                    projectId = doc.projectId,
                    status = "succeeded"
                )
                _isBusySubmitting.value = false
                _editorState.update {
                    it.copy(statusBanner = "Generated ${generated.files.size} Flutter files & packaged ZIP (${archive.sizeBytes / 1024} KB).")
                }
                onReady()
            }.onFailure { err ->
                _isBusySubmitting.value = false
                _editorState.update { it.copy(errorBanner = err.localizedMessage ?: "ZIP packaging failed.") }
            }
        }
    }

    fun submitApkBuildJob(context: Context, buildProfile: String) {
        val doc = _editorState.value.document ?: return
        if (_isBusySubmitting.value) return

        val profile = userProfileState.value
        if (profile != null && profile.planTier == "free" && profile.buildsUsed >= SketchToAppRepository.FREE_PLAN_BUILD_LIMIT) {
            _editorState.update {
                it.copy(errorBanner = "Free tier APK build quota reached (${SketchToAppRepository.FREE_PLAN_BUILD_LIMIT} builds).")
            }
            return
        }

        _isBusySubmitting.value = true
        viewModelScope.launch {
            val generated = GeminiAgentsService.generateAndAuditFlutterProject(doc)
            _generatedProject.value = generated
            if (!generated.isValid) {
                _isBusySubmitting.value = false
                _editorState.update { it.copy(errorBanner = "Fix schema diagnostics before submitting an APK build.") }
                return@launch
            }

            val zipArchive = _lastExportedZip.value
                ?: ZipAndArtifactExporter.createFlutterProjectZip(context, generated).getOrNull()
            if (zipArchive == null) {
                _isBusySubmitting.value = false
                _editorState.update { it.copy(errorBanner = "Unable to package Flutter ZIP snapshot for worker.") }
                return@launch
            }
            _lastExportedZip.value = zipArchive

            val genJobId = _lastGenerationJobId.value.ifBlank {
                repository.createGenerationJob(
                    projectId = doc.projectId,
                    fileCount = generated.files.size,
                    diagnosticsSummary = "Validated pre-build snapshot",
                    zipStorageRef = zipArchive.file.absolutePath,
                    zipChecksumSha256 = zipArchive.sha256Hex,
                    succeeded = true
                ).getOrDefault("gen_snapshot")
            }

            val workerConnected = GeminiAgentsService.isRemoteWorkerConfigured()
            val workerMode = if (workerConnected) "remote_worker" else "unconfigured"
            val initialLog = buildString {
                appendLine("[BuildAgent] Immutable Flutter archive prepared: ${zipArchive.file.name}")
                appendLine("[BuildAgent] Archive SHA-256: ${zipArchive.sha256Hex}")
                appendLine("[BuildAgent] Profile: $buildProfile | Worker Mode: $workerMode")
            }

            val buildIdRes = repository.createBuildJob(
                projectId = doc.projectId,
                generationJobId = genJobId,
                buildProfile = buildProfile,
                workerMode = workerMode,
                initialLogs = initialLog
            )
            val buildId = buildIdRes.getOrElse { err ->
                _isBusySubmitting.value = false
                _editorState.update { it.copy(errorBanner = err.localizedMessage ?: "Failed to create build job.") }
                return@launch
            }

            repository.recordUsageAndIncrementQuota(
                actionType = "apk_build",
                projectId = doc.projectId,
                status = "started",
                incrementBuilds = true
            )

            if (!workerConnected) {
                // Honest, non-fabricated worker disclosure: never fake an APK binary!
                val honestLog = initialLog + "\n" +
                    "[BuildAgent] Remote isolated Flutter worker (BACKEND_API_URL) is not configured in Secrets.\n" +
                    "[BuildAgent] Untrusted user-generated Flutter code is never compiled inside the Android client.\n" +
                    "[BuildAgent] Action: Export the verified Flutter source ZIP now, or start /backend + /build-worker (Docker) and set BACKEND_API_URL in AI Studio Secrets."
                repository.updateBuildJobStatus(
                    buildId = buildId,
                    status = "failed",
                    exitCode = 2,
                    sanitizedLogs = honestLog
                )
                _isBusySubmitting.value = false
                return@launch
            }

            // Dispatch to live isolated remote Build Worker
            repository.updateBuildJobStatus(
                buildId = buildId,
                status = "preparing",
                sanitizedLogs = initialLog + "[BuildAgent] Uploading source snapshot to isolated worker...\n"
            )

            val remoteSubmit = GeminiAgentsService.submitRemoteApkBuild(
                zipFile = zipArchive.file,
                projectId = doc.projectId,
                buildProfile = buildProfile
            )
            remoteSubmit.onSuccess { initialRemote ->
                var latest = initialRemote
                var attempts = 0
                while (latest.status in setOf("queued", "preparing", "building", "validating") && attempts < 60) {
                    repository.updateBuildJobStatus(
                        buildId = buildId,
                        status = latest.status,
                        exitCode = latest.exitCode,
                        sanitizedLogs = latest.sanitizedLogs.ifBlank { initialLog }
                    )
                    delay(4000L)
                    attempts++
                    latest = GeminiAgentsService.pollRemoteApkBuild(latest.buildId).getOrElse { break }
                }

                if (latest.status == "succeeded" && latest.artifactDownloadUrl.isNotBlank() && latest.artifactSizeBytes > 0L) {
                    val artifactId = repository.registerGeneratedAppArtifact(
                        projectId = doc.projectId,
                        buildJobId = buildId,
                        appName = doc.appName,
                        packageName = doc.packageName,
                        buildProfile = buildProfile,
                        versionName = "1.0.0",
                        sizeBytes = latest.artifactSizeBytes,
                        checksumSha256 = latest.artifactSha256,
                        storageRef = "worker://${latest.buildId}/app-$buildProfile.apk",
                        downloadUrl = latest.artifactDownloadUrl,
                        verified = true
                    ).getOrDefault("")

                    repository.updateBuildJobStatus(
                        buildId = buildId,
                        status = "succeeded",
                        exitCode = 0,
                        sanitizedLogs = latest.sanitizedLogs,
                        artifactId = artifactId
                    )
                } else {
                    repository.updateBuildJobStatus(
                        buildId = buildId,
                        status = "failed",
                        exitCode = if (latest.exitCode == 0) 1 else latest.exitCode,
                        sanitizedLogs = latest.sanitizedLogs.ifBlank { "Remote worker failed or timed out." }
                    )
                }
            }.onFailure { err ->
                repository.updateBuildJobStatus(
                    buildId = buildId,
                    status = "failed",
                    exitCode = 1,
                    sanitizedLogs = initialLog + "[BuildAgent] Worker error: ${err.localizedMessage}"
                )
            }
            _isBusySubmitting.value = false
        }
    }

    fun downloadVerifiedApk(
        context: Context,
        artifact: GeneratedAppRecord,
        bearerToken: String?,
        onDownloaded: (ExportedArchiveResult) -> Unit
    ) {
        if (!artifact.verified || artifact.downloadUrl.isBlank()) {
            _editorState.update { it.copy(errorBanner = "Artifact is not verified or has no download URL.") }
            return
        }
        _downloadProgress.value = 0
        viewModelScope.launch {
            val res = ZipAndArtifactExporter.downloadVerifiedArtifact(
                context = context,
                downloadUrl = artifact.downloadUrl,
                bearerIdToken = bearerToken,
                safeFileName = "${artifact.appName}_${artifact.buildProfile}.apk",
                expectedSha256 = artifact.checksumSha256,
                onProgress = { pct -> _downloadProgress.value = pct }
            )
            _downloadProgress.value = null
            res.onSuccess { fileRes ->
                _editorState.update { it.copy(statusBanner = "Verified APK downloaded (${fileRes.sizeBytes / 1024} KB).") }
                onDownloaded(fileRes)
            }.onFailure { err ->
                _editorState.update { it.copy(errorBanner = err.localizedMessage ?: "APK download failed.") }
            }
        }
    }

    fun askBackendAssistant(question: String) {
        if (question.isBlank() || _assistantResponse.value is AsyncResource.Loading) return
        _assistantResponse.value = AsyncResource.Loading
        val doc = _editorState.value.document
        val summary = if (doc != null) {
            "App=${doc.appName}, Package=${doc.packageName}, Screens=${doc.screens.size}, WorkerConfigured=${GeminiAgentsService.isRemoteWorkerConfigured()}"
        } else {
            "No active project open. WorkerConfigured=${GeminiAgentsService.isRemoteWorkerConfigured()}"
        }
        viewModelScope.launch {
            val res = GeminiAgentsService.askBackendAssistant(question, summary)
            _assistantResponse.value = res.fold(
                onSuccess = { AsyncResource.Success(it) },
                onFailure = { AsyncResource.Error(it.localizedMessage ?: "Assistant request failed.") }
            )
        }
    }

    fun currentDiagnostics(): List<SchemaDiagnostic> {
        val doc = _editorState.value.document ?: return emptyList()
        return SchemaValidator.validate(doc).diagnostics
    }

    private companion object {
        const val TAG = "SketchWorkspaceVM"
    }
}
