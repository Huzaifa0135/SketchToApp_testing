package com.example.core.schema

enum class DiagnosticSeverity {
    ERROR,
    WARNING,
    INFO
}

data class SchemaDiagnostic(
    val code: String,
    val severity: DiagnosticSeverity,
    val message: String,
    val screenId: String = "",
    val componentId: String = ""
)

data class SchemaValidationResult(
    val isValidForGeneration: Boolean,
    val diagnostics: List<SchemaDiagnostic>
) {
    val errors: List<SchemaDiagnostic> get() = diagnostics.filter { it.severity == DiagnosticSeverity.ERROR }
    val warnings: List<SchemaDiagnostic> get() = diagnostics.filter { it.severity == DiagnosticSeverity.WARNING }
}

object SchemaValidator {
    private val HEX_COLOR_REGEX = Regex("^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")
    private val SAFE_ID_REGEX = Regex("^[a-zA-Z0-9_\\-]+$")

    fun validate(
        doc: CanonicalApplicationDocument,
        knownAssetIds: Set<String> = doc.assetIds.toSet()
    ): SchemaValidationResult {
        val diagnostics = mutableListOf<SchemaDiagnostic>()

        if (doc.appName.isBlank()) {
            diagnostics.add(
                SchemaDiagnostic(
                    code = "APP_NAME_EMPTY",
                    severity = DiagnosticSeverity.ERROR,
                    message = "Application name must not be empty."
                )
            )
        }

        if (doc.screens.isEmpty()) {
            diagnostics.add(
                SchemaDiagnostic(
                    code = "NO_SCREENS",
                    severity = DiagnosticSeverity.ERROR,
                    message = "Project must contain at least one screen before generating Flutter source."
                )
            )
            return SchemaValidationResult(isValidForGeneration = false, diagnostics = diagnostics)
        }

        // Duplicate screen IDs & routes
        val seenScreenIds = mutableSetOf<String>()
        val seenRoutes = mutableSetOf<String>()
        doc.screens.forEach { screen ->
            if (!SAFE_ID_REGEX.matches(screen.id)) {
                diagnostics.add(
                    SchemaDiagnostic(
                        code = "INVALID_SCREEN_ID",
                        severity = DiagnosticSeverity.ERROR,
                        message = "Screen ID '${screen.id}' contains invalid characters.",
                        screenId = screen.id
                    )
                )
            }
            if (!seenScreenIds.add(screen.id)) {
                diagnostics.add(
                    SchemaDiagnostic(
                        code = "DUPLICATE_SCREEN_ID",
                        severity = DiagnosticSeverity.ERROR,
                        message = "Duplicate screen ID '${screen.id}' detected.",
                        screenId = screen.id
                    )
                )
            }
            if (!seenRoutes.add(screen.route)) {
                diagnostics.add(
                    SchemaDiagnostic(
                        code = "DUPLICATE_SCREEN_ROUTE",
                        severity = DiagnosticSeverity.ERROR,
                        message = "Duplicate route '${screen.route}' on screen '${screen.name}'.",
                        screenId = screen.id
                    )
                )
            }
        }

        if (doc.screens.none { it.id == doc.initialScreenId }) {
            diagnostics.add(
                SchemaDiagnostic(
                    code = "MISSING_INITIAL_SCREEN",
                    severity = DiagnosticSeverity.ERROR,
                    message = "Initial screen '${doc.initialScreenId}' does not match any screen in the project."
                )
            )
        }

        // Theme color validation
        listOf(
            "primaryColorHex" to doc.theme.primaryColorHex,
            "accentColorHex" to doc.theme.accentColorHex,
            "backgroundColorHex" to doc.theme.backgroundColorHex,
            "surfaceColorHex" to doc.theme.surfaceColorHex
        ).forEach { (field, hex) ->
            if (!HEX_COLOR_REGEX.matches(hex)) {
                diagnostics.add(
                    SchemaDiagnostic(
                        code = "INVALID_THEME_COLOR",
                        severity = DiagnosticSeverity.ERROR,
                        message = "Theme token '$field' has invalid hex color '$hex' (expected #RRGGBB)."
                    )
                )
            }
        }

        val allScreenIds = doc.screens.map { it.id }.toSet()
        val globalComponentIds = mutableSetOf<String>()

        doc.screens.forEach { screen ->
            val screenComponentIds = screen.components.map { it.id }.toSet()

            screen.warnings.forEach { warn ->
                diagnostics.add(
                    SchemaDiagnostic(
                        code = "SCREEN_IMPORT_WARNING",
                        severity = DiagnosticSeverity.WARNING,
                        message = warn,
                        screenId = screen.id
                    )
                )
            }

            screen.components.forEach { comp ->
                if (!SAFE_ID_REGEX.matches(comp.id)) {
                    diagnostics.add(
                        SchemaDiagnostic(
                            code = "INVALID_COMPONENT_ID",
                            severity = DiagnosticSeverity.ERROR,
                            message = "Component ID '${comp.id}' contains invalid characters.",
                            screenId = screen.id,
                            componentId = comp.id
                        )
                    )
                }
                if (!globalComponentIds.add("${screen.id}:${comp.id}")) {
                    diagnostics.add(
                        SchemaDiagnostic(
                            code = "DUPLICATE_COMPONENT_ID",
                            severity = DiagnosticSeverity.ERROR,
                            message = "Duplicate component ID '${comp.id}' in screen '${screen.name}'.",
                            screenId = screen.id,
                            componentId = comp.id
                        )
                    )
                }
                if (comp.parentId.isNotBlank() && comp.parentId !in screenComponentIds) {
                    diagnostics.add(
                        SchemaDiagnostic(
                            code = "MISSING_PARENT_COMPONENT",
                            severity = DiagnosticSeverity.ERROR,
                            message = "Component '${comp.label}' references missing parent '${comp.parentId}'.",
                            screenId = screen.id,
                            componentId = comp.id
                        )
                    )
                }
                if (comp.navActionType == NavActionType.NAVIGATE_TO_SCREEN) {
                    if (comp.targetScreenId.isBlank() || comp.targetScreenId !in allScreenIds) {
                        diagnostics.add(
                            SchemaDiagnostic(
                                code = "INVALID_NAV_DESTINATION",
                                severity = DiagnosticSeverity.ERROR,
                                message = "Component '${comp.label}' navigates to missing screen ID '${comp.targetScreenId}'.",
                                screenId = screen.id,
                                componentId = comp.id
                            )
                        )
                    } else if (comp.navSuggestedByAi && !comp.navConfirmedByUser) {
                        diagnostics.add(
                            SchemaDiagnostic(
                                code = "UNCONFIRMED_AI_NAVIGATION",
                                severity = DiagnosticSeverity.WARNING,
                                message = "AI-suggested navigation on '${comp.label}' has not been confirmed yet.",
                                screenId = screen.id,
                                componentId = comp.id
                            )
                        )
                    }
                }
                if (comp.assetId.isNotBlank() && knownAssetIds.isNotEmpty() && comp.assetId !in knownAssetIds) {
                    diagnostics.add(
                        SchemaDiagnostic(
                            code = "MISSING_ASSET_REF",
                            severity = DiagnosticSeverity.WARNING,
                            message = "Component '${comp.label}' references asset '${comp.assetId}' which is not registered; safe placeholder will be generated.",
                            screenId = screen.id,
                            componentId = comp.id
                        )
                    )
                }
                if (comp.type == ComponentType.UNSUPPORTED) {
                    diagnostics.add(
                        SchemaDiagnostic(
                            code = "UNSUPPORTED_COMPONENT",
                            severity = DiagnosticSeverity.WARNING,
                            message = "Component '${comp.label}' uses an unsupported source element and will render via SketchPlaceholderBox.",
                            screenId = screen.id,
                            componentId = comp.id
                        )
                    )
                }
                if (comp.uncertaintyNote.isNotBlank()) {
                    diagnostics.add(
                        SchemaDiagnostic(
                            code = "AI_UNCERTAINTY",
                            severity = DiagnosticSeverity.INFO,
                            message = "${comp.label}: ${comp.uncertaintyNote}",
                            screenId = screen.id,
                            componentId = comp.id
                        )
                    )
                }
            }
        }

        doc.warnings.forEach { warn ->
            diagnostics.add(
                SchemaDiagnostic(
                    code = "DOC_WARNING",
                    severity = DiagnosticSeverity.WARNING,
                    message = warn
                )
            )
        }

        val hasBlockingError = diagnostics.any { it.severity == DiagnosticSeverity.ERROR }
        return SchemaValidationResult(
            isValidForGeneration = !hasBlockingError,
            diagnostics = diagnostics
        )
    }
}
