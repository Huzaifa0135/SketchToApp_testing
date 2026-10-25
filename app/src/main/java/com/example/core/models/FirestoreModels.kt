package com.example.core.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val photoUrl: String? = null,
    val planTier: String = "free",
    val onboardingCompleted: Boolean = false,
    val aiCallsUsed: Long = 0L,
    val buildsUsed: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ProjectRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val description: String = "",
    val packageName: String = "com.generated.sketchapp",
    val sourceType: String = "sketch",
    val stage: String = "draft",
    val screenCount: Long = 1L,
    val schemaVersion: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class DesignRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val projectId: String = "",
    val sourceType: String = "sketch",
    val sourceReference: String = "",
    val analysisStatus: String = "pending",
    val schemaVersion: Long = 1L,
    val warningCount: Long = 0L,
    val warningsSummary: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ApplicationDocumentRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val projectId: String = "",
    val appName: String = "",
    val initialScreenId: String = "",
    val schemaVersion: Long = 1L,
    val primaryColorHex: String = "#58E6D0",
    val accentColorHex: String = "#8B7CF6",
    val backgroundColorHex: String = "#0B1118",
    val surfaceColorHex: String = "#16212A",
    val fontFamily: String = "Inter",
    val darkMode: Boolean = true,
    val borderRadiusDp: Long = 12L,
    val screenOrder: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ScreenRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val projectId: String = "",
    val name: String = "Home",
    val route: String = "/home",
    val isInitial: Boolean = true,
    val orderIndex: Long = 0L,
    val components: List<Map<String, Any?>> = emptyList(),
    val warnings: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AssetRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val projectId: String = "",
    val name: String = "",
    val mimeType: String = "image/jpeg",
    val sizeBytes: Long = 0L,
    val storageRef: String = "",
    val checksumSha256: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class GenerationJobRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val projectId: String = "",
    val sourceSchemaVersion: Long = 1L,
    val status: String = "queued",
    val fileCount: Long = 0L,
    val diagnosticsSummary: String = "",
    val zipStorageRef: String = "",
    val zipChecksumSha256: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class BuildJobRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val projectId: String = "",
    val generationJobId: String = "",
    val buildProfile: String = "debug",
    val status: String = "queued",
    val workerMode: String = "unconfigured",
    val flutterVersion: String = "3.27.0",
    val exitCode: Long = -1L,
    val sanitizedLogs: String = "",
    val artifactId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class GeneratedAppRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val projectId: String = "",
    val buildJobId: String = "",
    val appName: String = "",
    val packageName: String = "",
    val buildProfile: String = "debug",
    val versionName: String = "1.0.0",
    val sizeBytes: Long = 0L,
    val checksumSha256: String = "",
    val storageRef: String = "",
    val downloadUrl: String = "",
    val expiresAtMs: Long = 0L,
    val verified: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UsageLogRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val actionType: String = "sketch_analysis",
    val projectId: String = "",
    val status: String = "succeeded",
    @ServerTimestamp val createdAt: Timestamp? = null
)
