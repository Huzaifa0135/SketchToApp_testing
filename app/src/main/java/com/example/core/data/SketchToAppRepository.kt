package com.example.core.data

import android.content.Context
import com.example.R
import com.example.core.firebase.OperationType
import com.example.core.firebase.handleFirestoreError
import com.example.core.models.ApplicationDocumentRecord
import com.example.core.models.AssetRecord
import com.example.core.models.BuildJobRecord
import com.example.core.models.DesignRecord
import com.example.core.models.GeneratedAppRecord
import com.example.core.models.GenerationJobRecord
import com.example.core.models.ProjectRecord
import com.example.core.models.ScreenRecord
import com.example.core.models.UsageLogRecord
import com.example.core.models.UserProfile
import com.example.core.schema.CURRENT_SCHEMA_VERSION
import com.example.core.schema.CanonicalApplicationDocument
import com.example.core.schema.CanonicalComponent
import com.example.core.schema.CanonicalScreen
import com.example.core.schema.CanonicalThemeTokens
import com.example.core.schema.ComponentType
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class SketchToAppRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        Firebase.auth
    )

    companion object {
        const val FREE_PLAN_AI_LIMIT = 25L
        const val FREE_PLAN_BUILD_LIMIT = 10L
    }

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun safeId(prefix: String): String {
        val raw = UUID.randomUUID().toString().replace("-", "").take(16)
        return "${prefix}_$raw"
    }

    // --- 1. User Profile & Quotas ---

    suspend fun ensureUserProfile(
        displayName: String,
        email: String?,
        photoUrl: String?
    ): Result<UserProfile> = runCatching {
        val uid = requireUserId()
        val path = "users/$uid"
        val docRef = db.collection("users").document(uid)
        try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: UserProfile(id = uid, userId = uid, displayName = displayName.ifBlank { "Developer" })
            } else {
                val payload = mapOf<String, Any?>(
                    "userId" to uid,
                    "displayName" to displayName.ifBlank { "Developer" }.take(80),
                    "email" to email?.take(200),
                    "photoUrl" to photoUrl?.take(500),
                    "planTier" to "free",
                    "onboardingCompleted" to false,
                    "aiCallsUsed" to 0L,
                    "buildsUsed" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
                docRef.set(payload).await()
                val created = docRef.get().await()
                created.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: UserProfile(id = uid, userId = uid, displayName = displayName.ifBlank { "Developer" })
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            throw e
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> {
        val path = "users/$userId"
        return db.collection("users").document(userId)
            .snapshots()
            .map { snap ->
                if (snap.exists()) {
                    snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.GET, path)
                throw error
            }
    }

    suspend fun updateProfilePreferences(
        displayName: String,
        onboardingCompleted: Boolean
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        try {
            val payload = mapOf<String, Any>(
                "displayName" to displayName.ifBlank { "Developer" }.take(80),
                "onboardingCompleted" to onboardingCompleted,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun recordUsageAndIncrementQuota(
        actionType: String,
        projectId: String,
        status: String,
        incrementAiCalls: Boolean = false,
        incrementBuilds: Boolean = false
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val logId = safeId("log")
        val logRef = db.collection("usage_logs").document(logId)
        try {
            logRef.set(
                mapOf(
                    "userId" to uid,
                    "actionType" to actionType,
                    "projectId" to projectId.ifBlank { "global" }.take(128),
                    "status" to status,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()

            if (incrementAiCalls || incrementBuilds) {
                val userRef = db.collection("users").document(uid)
                val snap = userRef.get().await()
                if (snap.exists()) {
                    val currentAi = snap.getLong("aiCallsUsed") ?: 0L
                    val currentBuilds = snap.getLong("buildsUsed") ?: 0L
                    userRef.update(
                        mapOf(
                            "aiCallsUsed" to if (incrementAiCalls) currentAi + 1L else currentAi,
                            "buildsUsed" to if (incrementBuilds) currentBuilds + 1L else currentBuilds,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, logRef.path)
            throw e
        }
    }

    fun observeUsageLogs(userId: String): Flow<List<UsageLogRecord>> {
        val path = "usage_logs"
        return db.collection(path)
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(UsageLogRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                    .take(50)
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    // --- 2. Projects & Initial Canonical Document ---

    fun observeProjects(userId: String): Flow<List<ProjectRecord>> = flow {
        val path = "projects"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(ProjectRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun getProjectById(projectId: String): Result<ProjectRecord> = runCatching {
        val docRef = db.collection("projects").document(projectId)
        try {
            val snap = docRef.get().await()
            if (!snap.exists()) error("Project not found: $projectId")
            snap.toObject(ProjectRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: error("Failed to deserialize project $projectId")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun createProject(
        name: String,
        description: String,
        packageName: String,
        sourceType: String = "sketch"
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val projectId = safeId("proj")
        val initialScreenId = safeId("scr")
        val cleanName = name.trim().ifBlank { "Untitled App" }.take(80)
        val cleanPkg = packageName.trim().ifBlank { "com.generated.sketchapp" }.take(120)

        val projRef = db.collection("projects").document(projectId)
        try {
            // 1. Create parent project first so Parent Gate passes on child writes
            projRef.set(
                mapOf(
                    "userId" to uid,
                    "name" to cleanName,
                    "description" to description.trim().take(500),
                    "packageName" to cleanPkg,
                    "sourceType" to sourceType,
                    "stage" to "draft",
                    "screenCount" to 1L,
                    "schemaVersion" to CURRENT_SCHEMA_VERSION.toLong(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // 2. Create canonical application_documents entry
            val defaultComponents = listOf(
                CanonicalComponent(
                    id = safeId("cmp"),
                    type = ComponentType.APP_BAR,
                    label = cleanName
                ),
                CanonicalComponent(
                    id = safeId("cmp"),
                    type = ComponentType.TEXT,
                    label = "Welcome to $cleanName",
                    fontSizeSp = 22,
                    fontWeight = "bold"
                ),
                CanonicalComponent(
                    id = safeId("cmp"),
                    type = ComponentType.TEXT_FIELD,
                    label = "Email Address",
                    hint = "you@example.com",
                    formRequired = true
                ),
                CanonicalComponent(
                    id = safeId("cmp"),
                    type = ComponentType.BUTTON,
                    label = "Get Started",
                    backgroundColorHex = "#58E6D0",
                    textColorHex = "#0B1118"
                )
            )

            db.collection("application_documents").document(projectId).set(
                mapOf(
                    "userId" to uid,
                    "projectId" to projectId,
                    "appName" to cleanName,
                    "initialScreenId" to initialScreenId,
                    "schemaVersion" to CURRENT_SCHEMA_VERSION.toLong(),
                    "primaryColorHex" to "#58E6D0",
                    "accentColorHex" to "#8B7CF6",
                    "backgroundColorHex" to "#0B1118",
                    "surfaceColorHex" to "#16212A",
                    "fontFamily" to "Inter",
                    "darkMode" to true,
                    "borderRadiusDp" to 12L,
                    "screenOrder" to listOf(initialScreenId),
                    "warnings" to emptyList<String>(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // 3. Create initial Home screen
            db.collection("screens").document(initialScreenId).set(
                mapOf(
                    "userId" to uid,
                    "projectId" to projectId,
                    "name" to "Home",
                    "route" to "/home",
                    "isInitial" to true,
                    "orderIndex" to 0L,
                    "components" to defaultComponents.map { it.toFirestoreMap() },
                    "warnings" to emptyList<String>(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            projectId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, projRef.path)
            throw e
        }
    }

    suspend fun renameOrUpdateProject(
        projectId: String,
        name: String? = null,
        description: String? = null,
        packageName: String? = null,
        stage: String? = null,
        screenCount: Int? = null,
        sourceType: String? = null
    ): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("projects").document(projectId)
        try {
            val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
            if (name != null) updates["name"] = name.trim().ifBlank { "Untitled App" }.take(80)
            if (description != null) updates["description"] = description.trim().take(500)
            if (packageName != null) updates["packageName"] = packageName.trim().ifBlank { "com.generated.sketchapp" }.take(120)
            if (stage != null) updates["stage"] = stage
            if (screenCount != null) updates["screenCount"] = screenCount.toLong()
            if (sourceType != null) updates["sourceType"] = sourceType
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun duplicateProject(sourceProjectId: String): Result<String> = runCatching {
        val uid = requireUserId()
        val sourceProj = getProjectById(sourceProjectId).getOrThrow()
        val sourceDoc = loadCanonicalDocument(sourceProjectId).getOrThrow()

        val newProjId = safeId("proj")
        val copyName = "${sourceProj.name.take(65)} (Copy)"

        db.collection("projects").document(newProjId).set(
            mapOf(
                "userId" to uid,
                "name" to copyName,
                "description" to sourceProj.description,
                "packageName" to "${sourceProj.packageName}.copy".take(120),
                "sourceType" to sourceProj.sourceType,
                "stage" to "edited",
                "screenCount" to sourceDoc.screens.size.toLong().coerceAtLeast(1L),
                "schemaVersion" to CURRENT_SCHEMA_VERSION.toLong(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        // Remap screen IDs so duplicated screens have distinct Firestore keys
        val screenIdMap = sourceDoc.screens.associate { it.id to safeId("scr") }
        val remappedScreens = sourceDoc.screens.map { screen ->
            val newSid = screenIdMap[screen.id] ?: safeId("scr")
            screen.copy(
                id = newSid,
                components = screen.components.map { cmp ->
                    cmp.copy(
                        id = safeId("cmp"),
                        targetScreenId = screenIdMap[cmp.targetScreenId] ?: cmp.targetScreenId
                    )
                }
            )
        }
        val newInitialScreenId = screenIdMap[sourceDoc.initialScreenId]
            ?: remappedScreens.firstOrNull()?.id.orEmpty()

        val newCanonical = sourceDoc.copy(
            projectId = newProjId,
            appName = copyName,
            initialScreenId = newInitialScreenId,
            screens = remappedScreens
        )
        saveCanonicalDocument(newCanonical, isNewAppDoc = true).getOrThrow()
        newProjId
    }

    suspend fun deleteProjectCascade(projectId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val collectionsToPurge = listOf(
            "screens",
            "designs",
            "assets",
            "generation_jobs",
            "build_jobs",
            "generated_apps"
        )
        try {
            for (col in collectionsToPurge) {
                val snap = db.collection(col)
                    .whereEqualTo("userId", uid)
                    .whereEqualTo("projectId", projectId)
                    .get()
                    .await()
                for (doc in snap.documents) {
                    doc.reference.delete().await()
                }
            }
            val appDocRef = db.collection("application_documents").document(projectId)
            val appDocSnap = appDocRef.get().await()
            if (appDocSnap.exists()) {
                appDocRef.delete().await()
            }
            db.collection("projects").document(projectId).delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "projects/$projectId")
            throw e
        }
    }

    // --- 3. Canonical Application Document & Screens ---

    suspend fun loadCanonicalDocument(projectId: String): Result<CanonicalApplicationDocument> = runCatching {
        val uid = requireUserId()
        val proj = getProjectById(projectId).getOrThrow()
        val appDocSnap = db.collection("application_documents").document(projectId).get().await()
        val appDoc = appDocSnap.toObject(ApplicationDocumentRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)

        val screensSnap = db.collection("screens")
            .whereEqualTo("userId", uid)
            .whereEqualTo("projectId", projectId)
            .get()
            .await()

        val screenRecords = screensSnap.toObjects(ScreenRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedBy { it.orderIndex }

        val canonicalScreens = screenRecords.map { rec ->
            CanonicalScreen(
                id = rec.id,
                name = rec.name,
                route = rec.route,
                isInitial = rec.isInitial,
                orderIndex = rec.orderIndex.toInt(),
                components = rec.components.map { CanonicalComponent.fromFirestoreMap(it) },
                warnings = rec.warnings
            )
        }

        val assetsSnap = db.collection("assets")
            .whereEqualTo("userId", uid)
            .whereEqualTo("projectId", projectId)
            .get()
            .await()
        val assetIds = assetsSnap.documents.map { it.id }

        val resolvedInitial = appDoc?.initialScreenId?.takeIf { id -> canonicalScreens.any { it.id == id } }
            ?: canonicalScreens.firstOrNull { it.isInitial }?.id
            ?: canonicalScreens.firstOrNull()?.id.orEmpty()

        CanonicalApplicationDocument(
            projectId = projectId,
            appName = appDoc?.appName?.ifBlank { proj.name } ?: proj.name,
            packageName = proj.packageName,
            schemaVersion = (appDoc?.schemaVersion ?: CURRENT_SCHEMA_VERSION.toLong()).toInt(),
            initialScreenId = resolvedInitial,
            theme = CanonicalThemeTokens(
                primaryColorHex = appDoc?.primaryColorHex ?: "#58E6D0",
                accentColorHex = appDoc?.accentColorHex ?: "#8B7CF6",
                backgroundColorHex = appDoc?.backgroundColorHex ?: "#0B1118",
                surfaceColorHex = appDoc?.surfaceColorHex ?: "#16212A",
                fontFamily = appDoc?.fontFamily ?: "Inter",
                darkMode = appDoc?.darkMode ?: true,
                borderRadiusDp = (appDoc?.borderRadiusDp ?: 12L).toInt()
            ),
            screens = canonicalScreens,
            assetIds = assetIds,
            warnings = appDoc?.warnings ?: emptyList()
        )
    }

    suspend fun saveCanonicalDocument(
        doc: CanonicalApplicationDocument,
        isNewAppDoc: Boolean = false
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val appDocRef = db.collection("application_documents").document(doc.projectId)
        try {
            val existingAppDoc = if (isNewAppDoc) false else appDocRef.get().await().exists()
            val screenOrder = doc.screens.map { it.id }.take(30)
            val boundedWarnings = doc.warnings.map { it.take(500) }.take(50)

            if (!existingAppDoc) {
                appDocRef.set(
                    mapOf(
                        "userId" to uid,
                        "projectId" to doc.projectId,
                        "appName" to doc.appName.ifBlank { "Sketch App" }.take(80),
                        "initialScreenId" to doc.initialScreenId.take(128),
                        "schemaVersion" to doc.schemaVersion.toLong(),
                        "primaryColorHex" to doc.theme.primaryColorHex,
                        "accentColorHex" to doc.theme.accentColorHex,
                        "backgroundColorHex" to doc.theme.backgroundColorHex,
                        "surfaceColorHex" to doc.theme.surfaceColorHex,
                        "fontFamily" to doc.theme.fontFamily.take(60),
                        "darkMode" to doc.theme.darkMode,
                        "borderRadiusDp" to doc.theme.borderRadiusDp.toLong().coerceIn(0L, 48L),
                        "screenOrder" to screenOrder,
                        "warnings" to boundedWarnings,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                appDocRef.update(
                    mapOf(
                        "appName" to doc.appName.ifBlank { "Sketch App" }.take(80),
                        "initialScreenId" to doc.initialScreenId.take(128),
                        "schemaVersion" to doc.schemaVersion.toLong(),
                        "primaryColorHex" to doc.theme.primaryColorHex,
                        "accentColorHex" to doc.theme.accentColorHex,
                        "backgroundColorHex" to doc.theme.backgroundColorHex,
                        "surfaceColorHex" to doc.theme.surfaceColorHex,
                        "fontFamily" to doc.theme.fontFamily.take(60),
                        "darkMode" to doc.theme.darkMode,
                        "borderRadiusDp" to doc.theme.borderRadiusDp.toLong().coerceIn(0L, 48L),
                        "screenOrder" to screenOrder,
                        "warnings" to boundedWarnings,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            // Reconcile screens: remove deleted screens, upsert active screens
            val existingScreensSnap = db.collection("screens")
                .whereEqualTo("userId", uid)
                .whereEqualTo("projectId", doc.projectId)
                .get()
                .await()
            val existingIds = existingScreensSnap.documents.map { it.id }.toSet()
            val activeIds = doc.screens.map { it.id }.toSet()

            for (staleId in existingIds - activeIds) {
                db.collection("screens").document(staleId).delete().await()
            }

            doc.screens.forEachIndexed { idx, screen ->
                val scrRef = db.collection("screens").document(screen.id)
                val compMaps = screen.components.take(100).map { it.toFirestoreMap() }
                val scrWarnings = screen.warnings.map { it.take(500) }.take(30)
                val isInit = screen.id == doc.initialScreenId

                if (screen.id in existingIds) {
                    scrRef.update(
                        mapOf(
                            "name" to screen.name.ifBlank { "Screen ${idx + 1}" }.take(80),
                            "route" to screen.route.ifBlank { "/screen_${idx + 1}" }.take(120),
                            "isInitial" to isInit,
                            "orderIndex" to idx.toLong(),
                            "components" to compMaps,
                            "warnings" to scrWarnings,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else {
                    scrRef.set(
                        mapOf(
                            "userId" to uid,
                            "projectId" to doc.projectId,
                            "name" to screen.name.ifBlank { "Screen ${idx + 1}" }.take(80),
                            "route" to screen.route.ifBlank { "/screen_${idx + 1}" }.take(120),
                            "isInitial" to isInit,
                            "orderIndex" to idx.toLong(),
                            "components" to compMaps,
                            "warnings" to scrWarnings,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }

            renameOrUpdateProject(
                projectId = doc.projectId,
                name = doc.appName,
                packageName = doc.packageName,
                screenCount = doc.screens.size,
                stage = "edited"
            ).getOrThrow()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, appDocRef.path)
            throw e
        }
    }

    // --- 4. Designs & Assets ---

    suspend fun saveDesignRecord(
        projectId: String,
        sourceType: String,
        sourceReference: String,
        analysisStatus: String,
        warningCount: Int,
        warningsSummary: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val designId = safeId("des")
        val ref = db.collection("designs").document(designId)
        try {
            ref.set(
                mapOf(
                    "userId" to uid,
                    "projectId" to projectId,
                    "sourceType" to sourceType,
                    "sourceReference" to sourceReference.ifBlank { "local://import" }.take(1000),
                    "analysisStatus" to analysisStatus,
                    "schemaVersion" to CURRENT_SCHEMA_VERSION.toLong(),
                    "warningCount" to warningCount.toLong().coerceIn(0L, 500L),
                    "warningsSummary" to warningsSummary.take(2000),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            designId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            throw e
        }
    }

    suspend fun registerAsset(
        projectId: String,
        name: String,
        mimeType: String,
        sizeBytes: Long,
        storageRef: String,
        checksumSha256: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val assetId = safeId("ast")
        val ref = db.collection("assets").document(assetId)
        try {
            ref.set(
                mapOf(
                    "userId" to uid,
                    "projectId" to projectId,
                    "name" to name.ifBlank { "sketch.jpg" }.take(120),
                    "mimeType" to mimeType.ifBlank { "image/jpeg" }.take(100),
                    "sizeBytes" to sizeBytes.coerceIn(0L, 52428800L),
                    "storageRef" to storageRef.ifBlank { "local://cache/$assetId" }.take(1000),
                    "checksumSha256" to checksumSha256.take(128),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            assetId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            throw e
        }
    }

    // --- 5. Generation Jobs, Build Jobs & Artifacts ---

    suspend fun createGenerationJob(
        projectId: String,
        fileCount: Int,
        diagnosticsSummary: String,
        zipStorageRef: String,
        zipChecksumSha256: String,
        succeeded: Boolean
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val jobId = safeId("gen")
        val ref = db.collection("generation_jobs").document(jobId)
        try {
            ref.set(
                mapOf(
                    "userId" to uid,
                    "projectId" to projectId,
                    "sourceSchemaVersion" to CURRENT_SCHEMA_VERSION.toLong(),
                    "status" to if (succeeded) "succeeded" else "failed",
                    "fileCount" to fileCount.toLong().coerceIn(0L, 500L),
                    "diagnosticsSummary" to diagnosticsSummary.take(4000),
                    "zipStorageRef" to zipStorageRef.take(1000),
                    "zipChecksumSha256" to zipChecksumSha256.take(128),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            if (succeeded) {
                renameOrUpdateProject(projectId = projectId, stage = "generated")
            }
            jobId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            throw e
        }
    }

    fun observeBuildJobs(userId: String, projectId: String): Flow<List<BuildJobRecord>> {
        val path = "build_jobs"
        return db.collection(path)
            .whereEqualTo("userId", userId)
            .whereEqualTo("projectId", projectId)
            .snapshots()
            .map { snap ->
                snap.toObjects(BuildJobRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun createBuildJob(
        projectId: String,
        generationJobId: String,
        buildProfile: String,
        workerMode: String,
        initialLogs: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val buildId = safeId("bld")
        val ref = db.collection("build_jobs").document(buildId)
        try {
            ref.set(
                mapOf(
                    "userId" to uid,
                    "projectId" to projectId,
                    "generationJobId" to generationJobId,
                    "buildProfile" to buildProfile,
                    "status" to "queued",
                    "workerMode" to workerMode,
                    "flutterVersion" to "3.27.0",
                    "exitCode" to -1L,
                    "sanitizedLogs" to initialLogs.take(20000),
                    "artifactId" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            renameOrUpdateProject(projectId = projectId, stage = "building")
            buildId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            throw e
        }
    }

    suspend fun updateBuildJobStatus(
        buildId: String,
        status: String,
        exitCode: Int = -1,
        sanitizedLogs: String,
        artifactId: String = ""
    ): Result<Unit> = runCatching {
        requireUserId()
        val ref = db.collection("build_jobs").document(buildId)
        try {
            ref.update(
                mapOf(
                    "status" to status,
                    "exitCode" to exitCode.toLong(),
                    "sanitizedLogs" to sanitizedLogs.take(20000),
                    "artifactId" to artifactId.take(128),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            throw e
        }
    }

    suspend fun registerGeneratedAppArtifact(
        projectId: String,
        buildJobId: String,
        appName: String,
        packageName: String,
        buildProfile: String,
        versionName: String,
        sizeBytes: Long,
        checksumSha256: String,
        storageRef: String,
        downloadUrl: String,
        verified: Boolean
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val artifactId = safeId("apk")
        val ref = db.collection("generated_apps").document(artifactId)
        try {
            ref.set(
                mapOf(
                    "userId" to uid,
                    "projectId" to projectId,
                    "buildJobId" to buildJobId,
                    "appName" to appName.take(80),
                    "packageName" to packageName.take(120),
                    "buildProfile" to buildProfile,
                    "versionName" to versionName.take(40),
                    "sizeBytes" to sizeBytes.coerceAtLeast(0L),
                    "checksumSha256" to checksumSha256.take(128),
                    "storageRef" to storageRef.take(1000),
                    "downloadUrl" to downloadUrl.take(1000),
                    "expiresAtMs" to (System.currentTimeMillis() + 7L * 24L * 3600L * 1000L),
                    "verified" to verified,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            if (verified) {
                renameOrUpdateProject(projectId = projectId, stage = "ready")
            }
            artifactId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            throw e
        }
    }

    fun observeGeneratedApps(userId: String, projectId: String): Flow<List<GeneratedAppRecord>> {
        val path = "generated_apps"
        return db.collection(path)
            .whereEqualTo("userId", userId)
            .whereEqualTo("projectId", projectId)
            .snapshots()
            .map { snap ->
                snap.toObjects(GeneratedAppRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }
}
