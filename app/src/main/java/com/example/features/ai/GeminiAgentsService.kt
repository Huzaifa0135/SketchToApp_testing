package com.example.features.ai

import android.graphics.Bitmap
import android.util.Base64
import com.example.BuildConfig
import com.example.core.schema.CanonicalApplicationDocument
import com.example.core.schema.CanonicalComponent
import com.example.core.schema.CanonicalScreen
import com.example.core.schema.ComponentType
import com.example.core.schema.FormValidationRule
import com.example.core.schema.NavActionType
import com.example.core.schema.SchemaDiagnostic
import com.example.core.schema.SchemaValidator
import com.example.features.code_generation.FlutterProjectGenerator
import com.example.features.code_generation.GeneratedFlutterProject
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

// --- Gemini REST Wire Models ---

@Serializable
data class GeminiGenerateRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = null,
    val systemInstruction: GeminiContent? = null
)

@Serializable
data class GeminiContent(
    val parts: List<GeminiPart>
)

@Serializable
data class GeminiPart(
    val text: String? = null,
    val inlineData: GeminiInlineData? = null
)

@Serializable
data class GeminiInlineData(
    val mimeType: String,
    val data: String
)

@Serializable
data class GeminiGenerationConfig(
    val responseMimeType: String? = null,
    val responseSchema: JsonObject? = null,
    val temperature: Float? = null
)

@Serializable
data class GeminiGenerateResponse(
    val candidates: List<GeminiCandidate> = emptyList()
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null
)

// --- Structured Sketch Analysis Output Contract ---

@Serializable
data class AiDetectedElement(
    val id: String = "",
    val type: String = "TEXT",
    val label: String = "",
    val secondaryText: String = "",
    val hint: String = "",
    val alignment: String = "start",
    val fontSizeSp: Int = 16,
    val fontWeight: String = "normal",
    val suggestedTargetScreen: String = "",
    val isEmailField: Boolean = false,
    val isPasswordField: Boolean = false,
    val confidence: Double = 0.85,
    val uncertaintyNote: String = ""
)

@Serializable
data class AiSketchAnalysisPayload(
    val inferredScreenName: String = "Imported Screen",
    val detectedPrimaryHex: String = "#58E6D0",
    val warnings: List<String> = emptyList(),
    val elements: List<AiDetectedElement> = emptyList()
)

// --- Remote Build Worker API Wire Models ---

@Serializable
data class RemoteBuildJobResponse(
    val buildId: String,
    val status: String,
    val flutterVersion: String = "3.27.0",
    val exitCode: Int = -1,
    val sanitizedLogs: String = "",
    val artifactDownloadUrl: String = "",
    val artifactSha256: String = "",
    val artifactSizeBytes: Long = 0L
)

interface GeminiRestApi {
    @POST("v1beta/models/gemini-3.1-pro-preview:generateContent")
    suspend fun generateProContent(
        @Query("key") apiKey: String,
        @Body request: GeminiGenerateRequest
    ): GeminiGenerateResponse

    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateFlashContent(
        @Query("key") apiKey: String,
        @Body request: GeminiGenerateRequest
    ): GeminiGenerateResponse
}

interface RemoteBackendBuildApi {
    @Multipart
    @POST("api/v1/builds")
    suspend fun submitBuildJob(
        @Header("Authorization") bearerToken: String,
        @Part("projectId") projectId: okhttp3.RequestBody,
        @Part("buildProfile") buildProfile: okhttp3.RequestBody,
        @Part sourceZip: MultipartBody.Part
    ): RemoteBuildJobResponse

    @GET("api/v1/builds/{buildId}")
    suspend fun getBuildStatus(
        @Header("Authorization") bearerToken: String,
        @Path("buildId") buildId: String
    ): RemoteBuildJobResponse
}

object GeminiAgentsService {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val geminiApi: GeminiRestApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GeminiRestApi::class.java)
    }

    fun isGeminiKeyConfigured(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return key.isNotBlank() && key != "MY_GEMINI_API_KEY" && !key.startsWith("YOUR_")
    }

    fun isRemoteWorkerConfigured(): Boolean {
        val url = BuildConfig.BACKEND_API_URL.trim()
        return url.isNotBlank() &&
            !url.contains("your-sketchtoapp-backend.example.com") &&
            (url.startsWith("https://") || url.startsWith("http://10.0.2.2"))
    }

    private fun Bitmap.toJpegBase64(): String {
        val maxSide = 1280
        val ratio = minOf(maxSide.toFloat() / width.coerceAtLeast(1), maxSide.toFloat() / height.coerceAtLeast(1), 1f)
        val scaled = if (ratio < 1f) {
            Bitmap.createScaledBitmap(
                this,
                (width * ratio).toInt().coerceAtLeast(1),
                (height * ratio).toInt().coerceAtLeast(1),
                true
            )
        } else {
            this
        }
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * A. Design Analysis Agent
     * Analyzes hand-drawn sketch image via Gemini 3.1 Pro multimodal structured JSON schema.
     */
    suspend fun analyzeSketchImage(
        bitmap: Bitmap,
        projectContextNote: String = ""
    ): Result<AiSketchAnalysisPayload> = withContext(Dispatchers.IO) {
        runCatching {
            if (!isGeminiKeyConfigured()) {
                error("GEMINI_API_KEY is not configured. Open the Secrets panel in AI Studio and set GEMINI_API_KEY to enable live AI sketch analysis.")
            }

            val schema = buildJsonObject {
                put("type", "OBJECT")
                putJsonObject("properties") {
                    putJsonObject("inferredScreenName") { put("type", "STRING") }
                    putJsonObject("detectedPrimaryHex") { put("type", "STRING") }
                    putJsonObject("warnings") {
                        put("type", "ARRAY")
                        putJsonObject("items") { put("type", "STRING") }
                    }
                    putJsonObject("elements") {
                        put("type", "ARRAY")
                        putJsonObject("items") {
                            put("type", "OBJECT")
                            putJsonObject("properties") {
                                putJsonObject("id") { put("type", "STRING") }
                                putJsonObject("type") {
                                    put("type", "STRING")
                                    putJsonArray("enum") {
                                        listOf(
                                            "APP_BAR", "TEXT", "BUTTON", "TEXT_FIELD", "IMAGE",
                                            "CARD", "CONTAINER", "ROW", "COLUMN", "LIST",
                                            "NAV_BAR", "DIVIDER", "UNSUPPORTED"
                                        ).forEach { add(it) }
                                    }
                                }
                                putJsonObject("label") { put("type", "STRING") }
                                putJsonObject("secondaryText") { put("type", "STRING") }
                                putJsonObject("hint") { put("type", "STRING") }
                                putJsonObject("alignment") { put("type", "STRING") }
                                putJsonObject("fontSizeSp") { put("type", "INTEGER") }
                                putJsonObject("fontWeight") { put("type", "STRING") }
                                putJsonObject("suggestedTargetScreen") { put("type", "STRING") }
                                putJsonObject("isEmailField") { put("type", "BOOLEAN") }
                                putJsonObject("isPasswordField") { put("type", "BOOLEAN") }
                                putJsonObject("confidence") { put("type", "NUMBER") }
                                putJsonObject("uncertaintyNote") { put("type", "STRING") }
                            }
                            putJsonArray("required") {
                                listOf("type", "label", "confidence").forEach { add(it) }
                            }
                        }
                    }
                }
                putJsonArray("required") {
                    add("inferredScreenName")
                    add("elements")
                    add("warnings")
                }
            }

            val prompt = buildString {
                appendLine("Analyze this mobile UI wireframe/sketch top-to-bottom.")
                appendLine("Return ONLY structured JSON matching the provided schema. Never return code.")
                appendLine("Rules:")
                appendLine("1. Identify visible UI elements in top-to-bottom visual reading order.")
                appendLine("2. Do NOT invent text that is illegible; set a concise generic label and explain ambiguity in uncertaintyNote.")
                appendLine("3. Record confidence between 0.0 and 1.0 for each element.")
                appendLine("4. If a button implies navigating to another screen (e.g. 'Sign Up', 'Forgot Password', 'Details'), populate suggestedTargetScreen.")
                if (projectContextNote.isNotBlank()) {
                    appendLine("Project context: ${projectContextNote.take(300)}")
                }
            }

            val request = GeminiGenerateRequest(
                systemInstruction = GeminiContent(
                    parts = listOf(
                        GeminiPart(
                            text = "You are the SketchToApp AI Design Analysis Agent. Extract canonical mobile UI elements conservatively and accurately without inventing unreadable copy."
                        )
                    )
                ),
                contents = listOf(
                    GeminiContent(
                        parts = listOf(
                            GeminiPart(text = prompt),
                            GeminiPart(
                                inlineData = GeminiInlineData(
                                    mimeType = "image/jpeg",
                                    data = bitmap.toJpegBase64()
                                )
                            )
                        )
                    )
                ),
                generationConfig = GeminiGenerationConfig(
                    responseMimeType = "application/json",
                    responseSchema = schema,
                    temperature = 0.2f
                )
            )

            val response = geminiApi.generateProContent(BuildConfig.GEMINI_API_KEY, request)
            val rawJson = response.candidates.firstOrNull()
                ?.content?.parts?.firstOrNull()?.text
                ?: error("Gemini Design Analysis Agent returned an empty response.")

            val parsed = json.decodeFromString<AiSketchAnalysisPayload>(rawJson)
            require(parsed.elements.isNotEmpty()) {
                "No recognizable UI elements were detected in the uploaded sketch."
            }
            parsed
        }
    }

    /**
     * B. UI Mapping Agent
     * Maps structured AI detections into a validated CanonicalScreen and marks inferred navigation as unconfirmed.
     */
    fun mapDetectedElementsToScreen(
        payload: AiSketchAnalysisPayload,
        existingScreens: List<CanonicalScreen>
    ): CanonicalScreen {
        val screenId = "scr_" + UUID.randomUUID().toString().replace("-", "").take(12)
        val cleanName = payload.inferredScreenName.trim().ifBlank { "Imported Sketch" }.take(40)
        val slug = FlutterProjectGenerator.sanitizeSlug(cleanName)

        val components = payload.elements.mapIndexed { idx, el ->
            val resolvedType = ComponentType.entries.find {
                it.name.equals(el.type.trim(), ignoreCase = true)
            } ?: ComponentType.UNSUPPORTED

            val matchedScreen = if (el.suggestedTargetScreen.isNotBlank()) {
                existingScreens.firstOrNull {
                    it.name.equals(el.suggestedTargetScreen.trim(), ignoreCase = true)
                }
            } else {
                null
            }

            val formRule = when {
                el.isEmailField -> FormValidationRule.EMAIL
                el.isPasswordField -> FormValidationRule.MIN_LENGTH_6
                resolvedType == ComponentType.TEXT_FIELD -> FormValidationRule.NON_EMPTY
                else -> FormValidationRule.NONE
            }

            CanonicalComponent(
                id = "cmp_${idx + 1}_" + UUID.randomUUID().toString().replace("-", "").take(6),
                type = resolvedType,
                label = el.label.ifBlank { resolvedType.displayLabel }.take(120),
                secondaryText = el.secondaryText.take(160),
                hint = el.hint.take(100),
                fontSizeSp = el.fontSizeSp.coerceIn(12, 36),
                fontWeight = el.fontWeight.lowercase().takeIf { it in setOf("normal", "medium", "bold") } ?: "normal",
                alignment = el.alignment.lowercase().takeIf { it in setOf("start", "center", "end") } ?: "start",
                navActionType = if (matchedScreen != null) NavActionType.NAVIGATE_TO_SCREEN else NavActionType.NONE,
                targetScreenId = matchedScreen?.id.orEmpty(),
                navSuggestedByAi = matchedScreen != null,
                navConfirmedByUser = matchedScreen == null,
                formRequired = resolvedType == ComponentType.TEXT_FIELD,
                formRule = formRule,
                confidence = el.confidence.coerceIn(0.0, 1.0),
                uncertaintyNote = buildString {
                    if (el.uncertaintyNote.isNotBlank()) append(el.uncertaintyNote)
                    if (matchedScreen == null && el.suggestedTargetScreen.isNotBlank()) {
                        if (isNotEmpty()) append(" • ")
                        append("AI suggested navigating to '${el.suggestedTargetScreen}' (create screen & link in Screen Flow).")
                    }
                }
            )
        }

        return CanonicalScreen(
            id = screenId,
            name = cleanName,
            route = "/$slug",
            isInitial = existingScreens.isEmpty(),
            orderIndex = existingScreens.size,
            components = components,
            warnings = payload.warnings
        )
    }

    /**
     * C. Code Generation Agent & D. Quality Assurance Agent
     * Coordinates deterministic Flutter templates and runs structural QA checks.
     */
    fun generateAndAuditFlutterProject(doc: CanonicalApplicationDocument): GeneratedFlutterProject {
        val generated = FlutterProjectGenerator.generate(doc)
        if (!generated.isValid) return generated

        val qaExtraDiagnostics = mutableListOf<SchemaDiagnostic>()
        val hasPubspec = generated.files.any { it.relativePath == "pubspec.yaml" }
        val hasMain = generated.files.any { it.relativePath == "lib/main.dart" }

        if (!hasPubspec || !hasMain) {
            qaExtraDiagnostics.add(
                SchemaDiagnostic(
                    code = "QA_MISSING_CORE_FILE",
                    severity = com.example.core.schema.DiagnosticSeverity.ERROR,
                    message = "Generated Flutter bundle is missing pubspec.yaml or lib/main.dart."
                )
            )
        }

        generated.files.filter { it.language == "dart" }.forEach { dartFile ->
            val openBraces = dartFile.content.count { it == '{' }
            val closeBraces = dartFile.content.count { it == '}' }
            if (openBraces != closeBraces) {
                qaExtraDiagnostics.add(
                    SchemaDiagnostic(
                        code = "QA_UNBALANCED_BRACES",
                        severity = com.example.core.schema.DiagnosticSeverity.ERROR,
                        message = "Unbalanced braces detected in ${dartFile.relativePath} ($openBraces vs $closeBraces)."
                    )
                )
            }
        }

        val combined = generated.diagnostics + qaExtraDiagnostics
        val stillValid = combined.none { it.severity == com.example.core.schema.DiagnosticSeverity.ERROR }
        return generated.copy(diagnostics = combined, isValid = stillValid)
    }

    /**
     * E. Build Agent
     * Submits verified source ZIP to the configured remote Build Worker API.
     */
    suspend fun submitRemoteApkBuild(
        zipFile: File,
        projectId: String,
        buildProfile: String
    ): Result<RemoteBuildJobResponse> = withContext(Dispatchers.IO) {
        runCatching {
            if (!isRemoteWorkerConfigured()) {
                error(
                    "Isolated Build Worker URL (BACKEND_API_URL) is not configured yet. " +
                        "Deploy the included /backend + /build-worker Docker service and set BACKEND_API_URL in AI Studio Secrets to compile live APKs."
                )
            }
            val token = Firebase.auth.currentUser?.getIdToken(false)?.await()?.token
                ?: error("Authenticated Firebase ID token unavailable.")

            val baseUrl = BuildConfig.BACKEND_API_URL.trim().trimEnd('/') + "/"
            val api = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(RemoteBackendBuildApi::class.java)

            val zipPart = MultipartBody.Part.createFormData(
                "sourceArchive",
                zipFile.name,
                zipFile.asRequestBody("application/zip".toMediaType())
            )
            api.submitBuildJob(
                bearerToken = "Bearer $token",
                projectId = projectId.toRequestBody("text/plain".toMediaType()),
                buildProfile = buildProfile.toRequestBody("text/plain".toMediaType()),
                sourceZip = zipPart
            )
        }
    }

    suspend fun pollRemoteApkBuild(remoteBuildId: String): Result<RemoteBuildJobResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val token = Firebase.auth.currentUser?.getIdToken(false)?.await()?.token
                ?: error("Authenticated Firebase ID token unavailable.")
            val baseUrl = BuildConfig.BACKEND_API_URL.trim().trimEnd('/') + "/"
            val api = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(RemoteBackendBuildApi::class.java)
            api.getBuildStatus("Bearer $token", remoteBuildId)
        }
    }

    /**
     * F. Backend Assistant Agent
     * Explains setup, Firestore rules, Figma OAuth, or build diagnostics without accessing secrets.
     */
    suspend fun askBackendAssistant(
        question: String,
        projectSummary: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (!isGeminiKeyConfigured()) {
                error("Set GEMINI_API_KEY in the AI Studio Secrets panel to query the live Backend Assistant Agent.")
            }
            val boundedQuestion = question.trim().take(600)
            val request = GeminiGenerateRequest(
                systemInstruction = GeminiContent(
                    parts = listOf(
                        GeminiPart(
                            text = "You are the SketchToApp AI Backend & DevOps Assistant Agent. " +
                                "Give concise, accurate guidance on Firebase Auth/Firestore rules, Figma OAuth/token setup, " +
                                "Flutter code generation diagnostics, and configuring the isolated Docker APK build worker. " +
                                "Never ask for or output raw passwords or secret keys."
                        )
                    )
                ),
                contents = listOf(
                    GeminiContent(
                        parts = listOf(
                            GeminiPart(
                                text = "Project context (non-sensitive): ${projectSummary.take(400)}\n\nQuestion: $boundedQuestion"
                            )
                        )
                    )
                ),
                generationConfig = GeminiGenerationConfig(temperature = 0.3f)
            )
            val res = geminiApi.generateFlashContent(BuildConfig.GEMINI_API_KEY, request)
            res.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "No response returned from Backend Assistant Agent."
        }
    }
}
