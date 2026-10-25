package com.example.features.figma

import com.example.BuildConfig
import com.example.core.schema.CanonicalComponent
import com.example.core.schema.CanonicalScreen
import com.example.core.schema.ComponentType
import com.example.core.schema.FormValidationRule
import com.example.features.code_generation.FlutterProjectGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import java.util.UUID
import java.util.concurrent.TimeUnit

@Serializable
data class FigmaColor(
    val r: Double = 0.0,
    val g: Double = 0.0,
    val b: Double = 0.0,
    val a: Double = 1.0
) {
    fun toHex(): String {
        val ri = (r * 255.0).toInt().coerceIn(0, 255)
        val gi = (g * 255.0).toInt().coerceIn(0, 255)
        val bi = (b * 255.0).toInt().coerceIn(0, 255)
        return "#%02X%02X%02X".format(ri, gi, bi)
    }
}

@Serializable
data class FigmaPaint(
    val type: String = "SOLID",
    val visible: Boolean = true,
    val color: FigmaColor? = null
)

@Serializable
data class FigmaTypeStyle(
    val fontFamily: String? = null,
    val fontWeight: Int? = null,
    val fontSize: Double? = null,
    val textAlignHorizontal: String? = null
)

@Serializable
data class FigmaBBox(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val width: Double = 0.0,
    val height: Double = 0.0
)

@Serializable
data class FigmaEffect(
    val type: String = "",
    val visible: Boolean = true
)

@Serializable
data class FigmaNode(
    val id: String = "",
    val name: String = "",
    val type: String = "",
    val characters: String? = null,
    val layoutMode: String? = null,
    val fills: List<FigmaPaint> = emptyList(),
    val effects: List<FigmaEffect> = emptyList(),
    val style: FigmaTypeStyle? = null,
    val absoluteBoundingBox: FigmaBBox? = null,
    val children: List<FigmaNode> = emptyList()
)

@Serializable
data class FigmaFileResponse(
    val name: String = "Figma Design",
    val lastModified: String = "",
    val version: String = "",
    val document: FigmaNode = FigmaNode()
)

data class FigmaFrameSummary(
    val nodeId: String,
    val name: String,
    val pageName: String,
    val widthPx: Int,
    val heightPx: Int,
    val childCount: Int,
    val rawNode: FigmaNode
)

data class FigmaFileInspection(
    val fileKey: String,
    val fileName: String,
    val lastModified: String,
    val frames: List<FigmaFrameSummary>
)

interface FigmaRestApi {
    @GET("v1/files/{fileKey}")
    suspend fun getFile(
        @Header("X-Figma-Token") accessToken: String,
        @Path("fileKey") fileKey: String
    ): FigmaFileResponse
}

object FigmaImportService {

    private val FILE_KEY_REGEX = Regex("^[a-zA-Z0-9]{10,64}$")
    private val URL_KEY_REGEX = Regex("figma\\.com/(?:file|design)/([a-zA-Z0-9]{10,64})")

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    private val api: FigmaRestApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.figma.com/")
            .client(httpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(FigmaRestApi::class.java)
    }

    fun isFigmaTokenConfigured(): Boolean {
        val tok = BuildConfig.FIGMA_ACCESS_TOKEN.trim()
        return tok.isNotBlank() && tok != "YOUR_FIGMA_ACCESS_TOKEN" && !tok.startsWith("MY_")
    }

    fun extractFileKey(input: String): String? {
        val trimmed = input.trim()
        if (FILE_KEY_REGEX.matches(trimmed)) return trimmed
        val match = URL_KEY_REGEX.find(trimmed)
        return match?.groupValues?.getOrNull(1)
    }

    suspend fun fetchFigmaFrames(urlOrKey: String): Result<FigmaFileInspection> = withContext(Dispatchers.IO) {
        runCatching {
            val fileKey = extractFileKey(urlOrKey)
                ?: error("Invalid Figma URL or file identifier. Paste a valid https://www.figma.com/design/<fileKey>/... URL or file key.")

            if (!isFigmaTokenConfigured()) {
                error("FIGMA_ACCESS_TOKEN is not configured. Add your Figma read token in the AI Studio Secrets panel (or configure server-side Figma OAuth on BACKEND_API_URL) to import live Figma files.")
            }

            val response = try {
                api.getFile(BuildConfig.FIGMA_ACCESS_TOKEN.trim(), fileKey)
            } catch (e: HttpException) {
                when (e.code()) {
                    401 -> error("Figma authorization expired or invalid (HTTP 401). Verify FIGMA_ACCESS_TOKEN.")
                    403 -> error("Insufficient Figma permissions to access file '$fileKey' (HTTP 403).")
                    404 -> error("Figma file '$fileKey' was not found (HTTP 404).")
                    429 -> error("Figma API rate limit reached (HTTP 429). Wait a moment and retry.")
                    else -> error("Figma API request failed (HTTP ${e.code()}).")
                }
            }

            val frames = mutableListOf<FigmaFrameSummary>()
            for (canvas in response.document.children) {
                if (canvas.type == "CANVAS") {
                    for (node in canvas.children) {
                        if (node.type == "FRAME" || node.type == "COMPONENT" || node.type == "SECTION") {
                            frames.add(
                                FigmaFrameSummary(
                                    nodeId = node.id,
                                    name = node.name.ifBlank { "Untitled Frame" },
                                    pageName = canvas.name.ifBlank { "Page 1" },
                                    widthPx = (node.absoluteBoundingBox?.width ?: 390.0).toInt(),
                                    heightPx = (node.absoluteBoundingBox?.height ?: 844.0).toInt(),
                                    childCount = node.children.size,
                                    rawNode = node
                                )
                            )
                        }
                    }
                }
            }

            if (frames.isEmpty()) {
                error("Figma file '${response.name}' contains zero top-level frames to import.")
            }

            FigmaFileInspection(
                fileKey = fileKey,
                fileName = response.name,
                lastModified = response.lastModified,
                frames = frames
            )
        }
    }

    fun mapFrameToCanonicalScreen(
        frame: FigmaFrameSummary,
        orderIndex: Int,
        isInitial: Boolean
    ): CanonicalScreen {
        val warnings = mutableListOf<String>()
        val components = mutableListOf<CanonicalComponent>()

        fun visit(node: FigmaNode, depth: Int) {
            if (components.size >= 80 || depth > 5) return

            node.effects.filter { it.visible }.forEach { effect ->
                if (effect.type in setOf("LAYER_BLUR", "BACKGROUND_BLUR")) {
                    warnings.add("Figma effect '${effect.type}' on '${node.name}' is approximated in Flutter Material 3.")
                }
            }
            if (node.fills.any { it.visible && it.type == "IMAGE" }) {
                warnings.add("Image fill on '${node.name}' requires asset export; mapped to Image placeholder.")
            }

            val solidHex = node.fills.firstOrNull { it.visible && it.type == "SOLID" && it.color != null }
                ?.color?.toHex().orEmpty()

            val lowerName = node.name.lowercase()
            val mappedType: ComponentType? = when {
                node.type == "TEXT" -> ComponentType.TEXT
                lowerName.contains("appbar") || lowerName.contains("header") || lowerName.contains("top bar") -> ComponentType.APP_BAR
                lowerName.contains("navbar") || lowerName.contains("bottom nav") || lowerName.contains("tab bar") -> ComponentType.NAV_BAR
                lowerName.contains("button") || lowerName.contains("cta") || lowerName.startsWith("btn") -> ComponentType.BUTTON
                lowerName.contains("input") || lowerName.contains("field") || lowerName.contains("search") -> ComponentType.TEXT_FIELD
                node.fills.any { it.visible && it.type == "IMAGE" } || lowerName.contains("image") || lowerName.contains("avatar") || lowerName.contains("banner") -> ComponentType.IMAGE
                lowerName.contains("divider") || (node.type == "LINE") -> ComponentType.DIVIDER
                lowerName.contains("card") -> ComponentType.CARD
                node.layoutMode == "HORIZONTAL" && depth <= 2 -> ComponentType.ROW
                else -> null
            }

            if (mappedType != null) {
                val extractedLabel = node.characters
                    ?: node.children.firstOrNull { it.type == "TEXT" }?.characters
                    ?: node.name
                val fontSize = (node.style?.fontSize ?: 16.0).toInt().coerceIn(11, 40)
                val weight = when {
                    (node.style?.fontWeight ?: 400) >= 700 -> "bold"
                    (node.style?.fontWeight ?: 400) >= 500 -> "medium"
                    else -> "normal"
                }
                val align = when (node.style?.textAlignHorizontal) {
                    "CENTER" -> "center"
                    "RIGHT" -> "end"
                    else -> "start"
                }

                components.add(
                    CanonicalComponent(
                        id = "cmp_fg_" + UUID.randomUUID().toString().replace("-", "").take(8),
                        type = mappedType,
                        label = extractedLabel.ifBlank { mappedType.displayLabel }.take(120),
                        hint = if (mappedType == ComponentType.TEXT_FIELD) "Enter ${node.name.take(30)}" else "",
                        fontSizeSp = fontSize,
                        fontWeight = weight,
                        textColorHex = if (mappedType == ComponentType.TEXT && solidHex.isNotBlank()) solidHex else "#E8F5F4",
                        backgroundColorHex = if (mappedType != ComponentType.TEXT) solidHex else "",
                        widthDp = (node.absoluteBoundingBox?.width ?: 0.0).toInt().coerceIn(0, 600),
                        heightDp = (node.absoluteBoundingBox?.height ?: 0.0).toInt().coerceIn(0, 400),
                        alignment = align,
                        formRequired = mappedType == ComponentType.TEXT_FIELD,
                        formRule = if (mappedType == ComponentType.TEXT_FIELD && lowerName.contains("email")) {
                            FormValidationRule.EMAIL
                        } else if (mappedType == ComponentType.TEXT_FIELD) {
                            FormValidationRule.NON_EMPTY
                        } else {
                            FormValidationRule.NONE
                        }
                    )
                )
                // Avoid duplicating nested text already absorbed into button/input/appbar
                if (mappedType in setOf(ComponentType.BUTTON, ComponentType.TEXT_FIELD, ComponentType.APP_BAR, ComponentType.NAV_BAR)) {
                    return
                }
            }

            node.children.forEach { child -> visit(child, depth + 1) }
        }

        frame.rawNode.children.forEach { child -> visit(child, 1) }

        if (components.isEmpty()) {
            warnings.add("Frame '${frame.name}' contained custom vectors/groups without standard text or auto-layout layers; inserted editable starter placeholder.")
            components.add(
                CanonicalComponent(
                    id = "cmp_fg_" + UUID.randomUUID().toString().replace("-", "").take(8),
                    type = ComponentType.UNSUPPORTED,
                    label = frame.name,
                    uncertaintyNote = "Custom Figma vector group — refine or reclassify in Visual Editor."
                )
            )
        }

        val cleanName = frame.name.trim().ifBlank { "Figma Screen ${orderIndex + 1}" }.take(50)
        val slug = FlutterProjectGenerator.sanitizeSlug(cleanName)
        return CanonicalScreen(
            id = "scr_" + UUID.randomUUID().toString().replace("-", "").take(12),
            name = cleanName,
            route = "/${slug}_${orderIndex + 1}",
            isInitial = isInitial,
            orderIndex = orderIndex,
            components = components,
            warnings = warnings.distinct().take(25)
        )
    }
}
