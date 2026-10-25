package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.core.data.SketchToAppRepository
import com.example.features.auth.OnboardingScreen
import com.example.features.auth.SignInScreen
import com.example.features.auth.SplashInitializationScreen
import com.example.features.build_lab.ArtifactsScreen
import com.example.features.build_lab.BuildLabScreen
import com.example.features.build_lab.BuildLogsScreen
import com.example.features.build_lab.DiagnosticsScreen
import com.example.features.build_lab.FlutterCodeViewerScreen
import com.example.features.code_generation.ZipAndArtifactExporter
import com.example.features.dashboard.DashboardScreen
import com.example.features.dashboard.ProjectManagementScreen
import com.example.features.settings.AccountSettingsScreen
import com.example.features.settings.HelpPrivacyScreen
import com.example.features.settings.ProjectSettingsScreen
import com.example.features.settings.UsageQuotaScreen
import com.example.features.sketch_import.AiAnalysisReviewScreen
import com.example.features.sketch_import.FigmaImportScreen
import com.example.features.sketch_import.SketchUploadScreen
import com.example.features.visual_editor.ComponentTreeScreen
import com.example.features.visual_editor.InteractivePreviewScreen
import com.example.features.visual_editor.ScreenFlowScreen
import com.example.features.visual_editor.ScreenManagerScreen
import com.example.features.visual_editor.ThemePropertiesScreen
import com.example.features.visual_editor.VisualEditorScreen
import com.example.features.workspace.SketchWorkspaceViewModel
import com.example.navigation.WorkstationDestination
import com.example.ui.theme.SketchToAppTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SketchToAppTheme {
                SketchToAppRoot()
            }
        }
    }
}

@Composable
fun SketchToAppRoot() {
    val auth = remember { FirebaseAuth.getInstance() }
    var authReady by remember { mutableStateOf(false) }
    var currentUser by remember { mutableStateOf<FirebaseUser?>(auth.currentUser) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
            authReady = true
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    if (!authReady) {
        SplashInitializationScreen()
        return
    }

    val user = currentUser
    if (user == null) {
        SignInScreen(
            onAuthSuccess = {
                currentUser = auth.currentUser
            }
        )
        return
    }

    AuthenticatedWorkstationShell(
        user = user,
        onSignedOut = { currentUser = null }
    )
}

@Composable
private fun AuthenticatedWorkstationShell(
    user: FirebaseUser,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel: SketchWorkspaceViewModel = viewModel(
        key = "workspace_${user.uid}",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repository = SketchToAppRepository(context.applicationContext)
                return SketchWorkspaceViewModel(
                    repository = repository,
                    currentUserId = user.uid,
                    initialDisplayName = user.displayName ?: "Developer",
                    initialEmail = user.email,
                    initialPhotoUrl = user.photoUrl?.toString()
                ) as T
            }
        }
    )

    val userProfile by viewModel.userProfileState.collectAsStateWithLifecycle()
    val projectsState by viewModel.projectsState.collectAsStateWithLifecycle()
    val editorState by viewModel.editorState.collectAsStateWithLifecycle()
    val processedSketch by viewModel.processedSketchBitmap.collectAsStateWithLifecycle()
    val highContrast by viewModel.sketchHighContrast.collectAsStateWithLifecycle()
    val sketchAnalysisState by viewModel.sketchAnalysisState.collectAsStateWithLifecycle()
    val figmaInspectionState by viewModel.figmaInspectionState.collectAsStateWithLifecycle()
    val generatedProject by viewModel.generatedProject.collectAsStateWithLifecycle()
    val lastExportedZip by viewModel.lastExportedZip.collectAsStateWithLifecycle()
    val buildJobs by viewModel.buildJobsState.collectAsStateWithLifecycle()
    val artifacts by viewModel.generatedAppsState.collectAsStateWithLifecycle()
    val usageLogs by viewModel.usageLogsState.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusySubmitting.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val assistantState by viewModel.assistantResponse.collectAsStateWithLifecycle()

    if (userProfile != null && !userProfile!!.onboardingCompleted) {
        OnboardingScreen(
            onCompleteOnboarding = { viewModel.markOnboardingComplete() }
        )
        return
    }

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = WorkstationDestination.Dashboard
    ) {
        composable<WorkstationDestination.Dashboard> {
            DashboardScreen(
                userProfile = userProfile,
                projectsState = projectsState,
                onCreateWithSource = { name, desc, pkg, src ->
                    viewModel.createNewProject(name, desc, pkg, src) { pid ->
                        when (src) {
                            "sketch" -> navController.navigate(WorkstationDestination.SketchUpload(pid))
                            "figma" -> navController.navigate(WorkstationDestination.FigmaImport(pid))
                            else -> navController.navigate(WorkstationDestination.VisualEditor(pid))
                        }
                    }
                },
                onOpenProject = { proj ->
                    viewModel.openProject(proj.id)
                    navController.navigate(WorkstationDestination.VisualEditor(proj.id))
                },
                onNavigateProjects = { navController.navigate(WorkstationDestination.ProjectManagement) },
                onNavigateUsage = { navController.navigate(WorkstationDestination.UsageQuota) },
                onNavigateAccount = { navController.navigate(WorkstationDestination.AccountSettings) },
                onNavigateHelp = { navController.navigate(WorkstationDestination.HelpPrivacy) }
            )
        }

        composable<WorkstationDestination.ProjectManagement> {
            ProjectManagementScreen(
                projectsState = projectsState,
                onCreateProject = { name, desc, pkg, src ->
                    viewModel.createNewProject(name, desc, pkg, src) { pid ->
                        navController.navigate(WorkstationDestination.VisualEditor(pid))
                    }
                },
                onOpenProject = { proj ->
                    viewModel.openProject(proj.id)
                    navController.navigate(WorkstationDestination.VisualEditor(proj.id))
                },
                onDuplicateProject = { pid -> viewModel.duplicateProject(pid) },
                onDeleteProject = { pid -> viewModel.deleteProject(pid) },
                onBack = { navController.popBackStack() }
            )
        }

        composable<WorkstationDestination.SketchUpload> { entry ->
            val route = entry.toRoute<WorkstationDestination.SketchUpload>()
            LaunchedEffect(route.projectId) {
                if (viewModel.activeProjectId.value != route.projectId) {
                    viewModel.openProject(route.projectId)
                }
            }
            SketchUploadScreen(
                projectId = route.projectId,
                processedBitmap = processedSketch,
                highContrastEnabled = highContrast,
                analysisState = sketchAnalysisState,
                errorBanner = editorState.errorBanner,
                onDismissBanner = { viewModel.clearBanners() },
                onSelectPhotoUri = { uri -> viewModel.loadSketchFromUri(context, uri, route.projectId) },
                onUseBitmap = { bmp -> viewModel.setSketchFromBitmap(bmp) },
                onRotate90 = { viewModel.rotateSketch90() },
                onToggleHighContrast = { viewModel.toggleSketchHighContrast() },
                onClearSketch = { viewModel.clearSketchSelection() },
                onRunAnalysis = {
                    viewModel.runSketchAiAnalysis(route.projectId) {
                        navController.navigate(WorkstationDestination.AiAnalysisReview(route.projectId))
                    }
                },
                onOpenFigmaImport = {
                    navController.navigate(WorkstationDestination.FigmaImport(route.projectId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable<WorkstationDestination.FigmaImport> { entry ->
            val route = entry.toRoute<WorkstationDestination.FigmaImport>()
            FigmaImportScreen(
                projectId = route.projectId,
                inspectionState = figmaInspectionState,
                onInspectFile = { url -> viewModel.inspectFigmaFile(url) },
                onImportFrames = { selectedIds, replace ->
                    viewModel.importSelectedFigmaFrames(route.projectId, selectedIds, replace) {
                        navController.navigate(WorkstationDestination.VisualEditor(route.projectId)) {
                            popUpTo(WorkstationDestination.Dashboard)
                        }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable<WorkstationDestination.AiAnalysisReview> { entry ->
            val route = entry.toRoute<WorkstationDestination.AiAnalysisReview>()
            AiAnalysisReviewScreen(
                sketchBitmap = processedSketch,
                analysisState = sketchAnalysisState,
                onUpdateComponent = { id, fn -> viewModel.updatePendingReviewedComponent(id, fn) },
                onRemoveComponent = { id -> viewModel.removePendingReviewedComponent(id) },
                onAcceptScreen = { replace ->
                    viewModel.acceptReviewedSketchScreen(replace) {
                        navController.navigate(WorkstationDestination.VisualEditor(route.projectId)) {
                            popUpTo(WorkstationDestination.Dashboard)
                        }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable<WorkstationDestination.VisualEditor> { entry ->
            val route = entry.toRoute<WorkstationDestination.VisualEditor>()
            LaunchedEffect(route.projectId) {
                if (viewModel.activeProjectId.value != route.projectId || editorState.document == null) {
                    viewModel.openProject(route.projectId)
                }
            }
            VisualEditorScreen(
                editorState = editorState,
                onSelectScreen = { viewModel.selectScreen(it) },
                onSelectComponent = { viewModel.selectComponent(it) },
                onAddComponent = { viewModel.addComponentToSelectedScreen(it) },
                onUpdateSelectedComponent = { viewModel.updateSelectedComponent(it) },
                onMoveSelected = { viewModel.moveSelectedComponent(it) },
                onDuplicateSelected = { viewModel.duplicateSelectedComponent() },
                onDeleteSelected = { viewModel.deleteSelectedComponent() },
                onUndo = { viewModel.undoEdit() },
                onRedo = { viewModel.redoEdit() },
                onSave = { viewModel.saveActiveDocument() },
                onDismissBanner = { viewModel.clearBanners() },
                onNavigateSketchImport = { navController.navigate(WorkstationDestination.SketchUpload(route.projectId)) },
                onNavigateComponentTree = { navController.navigate(WorkstationDestination.ComponentTree(route.projectId)) },
                onNavigateScreenManager = { navController.navigate(WorkstationDestination.ScreenManager(route.projectId)) },
                onNavigateScreenFlow = { navController.navigate(WorkstationDestination.ScreenFlow(route.projectId)) },
                onNavigateTheme = { navController.navigate(WorkstationDestination.ThemeProperties(route.projectId)) },
                onNavigatePreview = { navController.navigate(WorkstationDestination.InteractivePreview(route.projectId)) },
                onNavigateCodeViewer = { navController.navigate(WorkstationDestination.FlutterCodeViewer(route.projectId)) },
                onNavigateBuildLab = { navController.navigate(WorkstationDestination.BuildLab(route.projectId)) },
                onNavigateProjectSettings = { navController.navigate(WorkstationDestination.ProjectSettings(route.projectId)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable<WorkstationDestination.ComponentTree> {
            ComponentTreeScreen(
                editorState = editorState,
                onSelectScreen = { viewModel.selectScreen(it) },
                onSelectComponent = { viewModel.selectComponent(it) },
                onMoveSelected = { viewModel.moveSelectedComponent(it) },
                onDuplicateSelected = { viewModel.duplicateSelectedComponent() },
                onDeleteSelected = { viewModel.deleteSelectedComponent() },
                onBack = { navController.popBackStack() }
            )
        }

        composable<WorkstationDestination.ScreenManager> {
            ScreenManagerScreen(
                document = editorState.document,
                onAddScreen = { viewModel.addScreen(it) },
                onRenameScreen = { id, newName -> viewModel.renameScreen(id, newName) },
                onDuplicateScreen = { viewModel.duplicateScreen(it) },
                onDeleteScreen = { viewModel.deleteScreen(it) },
                onSetInitialScreen = { viewModel.setInitialScreen(it) },
                onOpenScreenInEditor = { screenId ->
                    viewModel.selectScreen(screenId)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable<WorkstationDestination.ScreenFlow> {
            ScreenFlowScreen(
                document = editorState.document,
                onUpdateComponent = { scrId, cmpId, fn -> viewModel.updateComponentById(scrId, cmpId, fn) },
                onBack = { navController.popBackStack() }
            )
        }

        composable<WorkstationDestination.ThemeProperties> {
            ThemePropertiesScreen(
                document = editorState.document,
                onUpdateTheme = { fn -> viewModel.updateThemeAndAppMetadata(themeTransform = fn) },
                onSave = { viewModel.saveActiveDocument() },
                onBack = { navController.popBackStack() }
            )
        }

        composable<WorkstationDestination.InteractivePreview> {
            InteractivePreviewScreen(
                document = editorState.document,
                onBack = { navController.popBackStack() }
            )
        }

        composable<WorkstationDestination.FlutterCodeViewer> { entry ->
            val route = entry.toRoute<WorkstationDestination.FlutterCodeViewer>()
            FlutterCodeViewerScreen(
                generatedProject = generatedProject,
                exportedZip = lastExportedZip,
                isBusy = isBusy,
                statusBanner = editorState.statusBanner,
                errorBanner = editorState.errorBanner,
                onDismissBanner = { viewModel.clearBanners() },
                onGenerateAndPackageZip = { viewModel.runFlutterCodeGenerationAndExportZip(context) },
                onOpenDiagnostics = { navController.navigate(WorkstationDestination.Diagnostics(route.projectId)) },
                onOpenBuildLab = { navController.navigate(WorkstationDestination.BuildLab(route.projectId)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable<WorkstationDestination.Diagnostics> {
            DiagnosticsScreen(
                diagnostics = viewModel.currentDiagnostics(),
                onBack = { navController.popBackStack() }
            )
        }

        composable<WorkstationDestination.BuildLab> { entry ->
            val route = entry.toRoute<WorkstationDestination.BuildLab>()
            BuildLabScreen(
                buildJobs = buildJobs,
                isBusy = isBusy,
                errorBanner = editorState.errorBanner,
                statusBanner = editorState.statusBanner,
                onDismissBanner = { viewModel.clearBanners() },
                onSubmitBuild = { profile -> viewModel.submitApkBuildJob(context, profile) },
                onOpenLogs = { jobId -> navController.navigate(WorkstationDestination.BuildLogs(route.projectId, jobId)) },
                onOpenArtifacts = { navController.navigate(WorkstationDestination.Artifacts(route.projectId)) },
                onOpenCodeExport = { navController.navigate(WorkstationDestination.FlutterCodeViewer(route.projectId)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable<WorkstationDestination.BuildLogs> { entry ->
            val route = entry.toRoute<WorkstationDestination.BuildLogs>()
            BuildLogsScreen(
                buildJobs = buildJobs,
                selectedJobId = route.buildJobId,
                onBack = { navController.popBackStack() }
            )
        }

        composable<WorkstationDestination.Artifacts> {
            ArtifactsScreen(
                artifacts = artifacts,
                exportedZip = lastExportedZip,
                downloadProgress = downloadProgress,
                onDownloadApk = { art ->
                    scope.launch {
                        val idToken = runCatching { user.getIdToken(false).await().token }.getOrNull()
                        viewModel.downloadVerifiedApk(context, art, idToken) { downloaded ->
                            ZipAndArtifactExporter.shareExportedFile(
                                context = context,
                                file = downloaded.file,
                                mimeType = "application/vnd.android.package-archive",
                                chooserTitle = "Install or Share Verified APK"
                            )
                        }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable<WorkstationDestination.ProjectSettings> {
            ProjectSettingsScreen(
                document = editorState.document,
                onSaveMetadata = { name, pkg ->
                    viewModel.updateThemeAndAppMetadata(appName = name, packageName = pkg)
                    viewModel.saveActiveDocument()
                },
                onDeleteProject = {
                    val pid = viewModel.activeProjectId.value
                    viewModel.deleteProject(pid) {
                        navController.navigate(WorkstationDestination.Dashboard) {
                            popUpTo(WorkstationDestination.Dashboard) { inclusive = true }
                        }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable<WorkstationDestination.AccountSettings> {
            AccountSettingsScreen(
                userProfile = userProfile,
                onSaveDisplayName = { viewModel.updateAccountDisplayName(it) },
                onSignedOut = onSignedOut,
                onBack = { navController.popBackStack() }
            )
        }

        composable<WorkstationDestination.UsageQuota> {
            UsageQuotaScreen(
                userProfile = userProfile,
                usageLogs = usageLogs,
                onBack = { navController.popBackStack() }
            )
        }

        composable<WorkstationDestination.HelpPrivacy> {
            HelpPrivacyScreen(
                assistantState = assistantState,
                onAskAssistant = { viewModel.askBackendAssistant(it) },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
