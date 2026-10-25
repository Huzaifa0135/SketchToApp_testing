package com.example.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe @Serializable Navigation Compose routes covering all 23 required
 * functional SketchToApp AI Android workstation screens.
 */
object WorkstationDestination {
    @Serializable data object Onboarding
    @Serializable data object Dashboard
    @Serializable data object ProjectManagement
    @Serializable data class SketchUpload(val projectId: String)
    @Serializable data class FigmaImport(val projectId: String)
    @Serializable data class AiAnalysisReview(val projectId: String)
    @Serializable data class VisualEditor(val projectId: String)
    @Serializable data class ComponentTree(val projectId: String)
    @Serializable data class ScreenManager(val projectId: String)
    @Serializable data class ScreenFlow(val projectId: String)
    @Serializable data class ThemeProperties(val projectId: String)
    @Serializable data class InteractivePreview(val projectId: String)
    @Serializable data class FlutterCodeViewer(val projectId: String)
    @Serializable data class Diagnostics(val projectId: String)
    @Serializable data class BuildLab(val projectId: String)
    @Serializable data class BuildLogs(val projectId: String, val buildJobId: String = "")
    @Serializable data class Artifacts(val projectId: String)
    @Serializable data class ProjectSettings(val projectId: String)
    @Serializable data object AccountSettings
    @Serializable data object UsageQuota
    @Serializable data object HelpPrivacy
}
