package com.example.core.schema

import kotlinx.serialization.Serializable

const val CURRENT_SCHEMA_VERSION = 1

@Serializable
enum class ComponentType(val displayLabel: String, val flutterWidget: String) {
    APP_BAR("App Bar", "AppBar"),
    TEXT("Text", "Text"),
    BUTTON("Button", "FilledButton"),
    TEXT_FIELD("Text Field", "TextFormField"),
    IMAGE("Image / Placeholder", "ClipRRect"),
    CARD("Card", "Card"),
    CONTAINER("Container", "Container"),
    ROW("Row", "Row"),
    COLUMN("Column", "Column"),
    LIST("List", "ListView"),
    NAV_BAR("Bottom Nav Bar", "NavigationBar"),
    DIVIDER("Divider", "Divider"),
    UNSUPPORTED("Unsupported Element", "SketchPlaceholderBox")
}

@Serializable
enum class NavActionType {
    NONE,
    NAVIGATE_TO_SCREEN,
    NAVIGATE_BACK
}

@Serializable
enum class FormValidationRule(val label: String) {
    NONE("No Validation"),
    NON_EMPTY("Required Non-Empty"),
    EMAIL("Valid Email Format"),
    MIN_LENGTH_6("Minimum 6 Characters")
}

@Serializable
data class CanonicalThemeTokens(
    val primaryColorHex: String = "#58E6D0",
    val accentColorHex: String = "#8B7CF6",
    val backgroundColorHex: String = "#0B1118",
    val surfaceColorHex: String = "#16212A",
    val fontFamily: String = "Inter",
    val darkMode: Boolean = true,
    val borderRadiusDp: Int = 12
)

@Serializable
data class CanonicalComponent(
    val id: String,
    val parentId: String = "",
    val type: ComponentType = ComponentType.TEXT,
    val label: String = "Label",
    val secondaryText: String = "",
    val hint: String = "",
    val fontSizeSp: Int = 16,
    val fontWeight: String = "normal", // normal, medium, bold
    val textColorHex: String = "#E8F5F4",
    val backgroundColorHex: String = "",
    val widthDp: Int = 0, // 0 = fill/auto
    val heightDp: Int = 0, // 0 = wrap
    val paddingDp: Int = 12,
    val spacingDp: Int = 8,
    val alignment: String = "start", // start, center, end
    val navActionType: NavActionType = NavActionType.NONE,
    val targetScreenId: String = "",
    val navSuggestedByAi: Boolean = false,
    val navConfirmedByUser: Boolean = true,
    val formRequired: Boolean = false,
    val formRule: FormValidationRule = FormValidationRule.NONE,
    val assetId: String = "",
    val confidence: Double = 1.0,
    val uncertaintyNote: String = ""
) {
    fun toFirestoreMap(): Map<String, Any> {
        return mapOf<String, Any?>(
            "id" to id,
            "parentId" to parentId,
            "type" to type.name,
            "label" to label.take(240),
            "secondaryText" to secondaryText.take(240),
            "hint" to hint.take(160),
            "fontSizeSp" to fontSizeSp.toLong(),
            "fontWeight" to fontWeight.take(20),
            "textColorHex" to textColorHex.take(12),
            "backgroundColorHex" to backgroundColorHex.take(12),
            "widthDp" to widthDp.toLong(),
            "heightDp" to heightDp.toLong(),
            "paddingDp" to paddingDp.toLong(),
            "spacingDp" to spacingDp.toLong(),
            "alignment" to alignment.take(16),
            "navActionType" to navActionType.name,
            "targetScreenId" to targetScreenId.take(128),
            "navSuggestedByAi" to navSuggestedByAi,
            "navConfirmedByUser" to navConfirmedByUser,
            "formRequired" to formRequired,
            "formRule" to formRule.name,
            "assetId" to assetId.take(128),
            "confidence" to confidence,
            "uncertaintyNote" to uncertaintyNote.take(300)
        ).filterValues { it != null } as Map<String, Any>
    }

    companion object {
        fun fromFirestoreMap(map: Map<String, Any?>): CanonicalComponent {
            val rawType = (map["type"] as? String).orEmpty()
            val resolvedType = ComponentType.entries.find { it.name.equals(rawType, ignoreCase = true) }
                ?: ComponentType.UNSUPPORTED
            val rawNav = (map["navActionType"] as? String).orEmpty()
            val resolvedNav = NavActionType.entries.find { it.name.equals(rawNav, ignoreCase = true) }
                ?: NavActionType.NONE
            val rawForm = (map["formRule"] as? String).orEmpty()
            val resolvedForm = FormValidationRule.entries.find { it.name.equals(rawForm, ignoreCase = true) }
                ?: FormValidationRule.NONE

            return CanonicalComponent(
                id = (map["id"] as? String)?.ifBlank { "cmp_unknown" } ?: "cmp_unknown",
                parentId = (map["parentId"] as? String).orEmpty(),
                type = resolvedType,
                label = (map["label"] as? String).orEmpty(),
                secondaryText = (map["secondaryText"] as? String).orEmpty(),
                hint = (map["hint"] as? String).orEmpty(),
                fontSizeSp = ((map["fontSizeSp"] as? Number)?.toInt() ?: 16).coerceIn(10, 48),
                fontWeight = (map["fontWeight"] as? String)?.ifBlank { "normal" } ?: "normal",
                textColorHex = (map["textColorHex"] as? String)?.ifBlank { "#E8F5F4" } ?: "#E8F5F4",
                backgroundColorHex = (map["backgroundColorHex"] as? String).orEmpty(),
                widthDp = ((map["widthDp"] as? Number)?.toInt() ?: 0).coerceIn(0, 1200),
                heightDp = ((map["heightDp"] as? Number)?.toInt() ?: 0).coerceIn(0, 2000),
                paddingDp = ((map["paddingDp"] as? Number)?.toInt() ?: 12).coerceIn(0, 64),
                spacingDp = ((map["spacingDp"] as? Number)?.toInt() ?: 8).coerceIn(0, 48),
                alignment = (map["alignment"] as? String)?.ifBlank { "start" } ?: "start",
                navActionType = resolvedNav,
                targetScreenId = (map["targetScreenId"] as? String).orEmpty(),
                navSuggestedByAi = (map["navSuggestedByAi"] as? Boolean) ?: false,
                navConfirmedByUser = (map["navConfirmedByUser"] as? Boolean) ?: true,
                formRequired = (map["formRequired"] as? Boolean) ?: false,
                formRule = resolvedForm,
                assetId = (map["assetId"] as? String).orEmpty(),
                confidence = ((map["confidence"] as? Number)?.toDouble() ?: 1.0).coerceIn(0.0, 1.0),
                uncertaintyNote = (map["uncertaintyNote"] as? String).orEmpty()
            )
        }
    }
}

@Serializable
data class CanonicalScreen(
    val id: String,
    val name: String,
    val route: String,
    val isInitial: Boolean = false,
    val orderIndex: Int = 0,
    val components: List<CanonicalComponent> = emptyList(),
    val warnings: List<String> = emptyList()
)

@Serializable
data class CanonicalApplicationDocument(
    val projectId: String,
    val appName: String,
    val packageName: String = "com.generated.sketchapp",
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val initialScreenId: String,
    val theme: CanonicalThemeTokens = CanonicalThemeTokens(),
    val screens: List<CanonicalScreen> = emptyList(),
    val assetIds: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)
