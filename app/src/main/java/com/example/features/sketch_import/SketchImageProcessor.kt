package com.example.features.sketch_import

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import com.example.features.code_generation.ZipAndArtifactExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

data class LoadedSketchImage(
    val bitmap: Bitmap,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256Hex: String,
    val cachedFilePath: String
)

object SketchImageProcessor {
    private const val MAX_IMAGE_BYTES = 12L * 1024L * 1024L // 12 MB safety cap
    private val ALLOWED_MIMES = setOf("image/jpeg", "image/png", "image/webp")

    suspend fun loadAndValidateFromUri(
        context: Context,
        uri: Uri
    ): Result<LoadedSketchImage> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val mime = resolver.getType(uri)?.lowercase() ?: "image/jpeg"
            require(mime in ALLOWED_MIMES) {
                "Unsupported image format '$mime'. Please choose a PNG, JPEG, or WebP sketch image."
            }

            val rawBytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Could not read selected image file.")

            require(rawBytes.isNotEmpty()) { "Selected image file is empty." }
            require(rawBytes.size <= MAX_IMAGE_BYTES) {
                "Image exceeds 12 MB upload limit (${rawBytes.size / (1024 * 1024)} MB). Choose a smaller image."
            }

            val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                ?: error("Failed to decode image bitmap. File may be corrupted.")

            val sketchesDir = File(context.cacheDir, "sketches").apply { mkdirs() }
            val cachedFile = File(sketchesDir, "sketch_${System.currentTimeMillis()}.jpg")
            FileOutputStream(cachedFile).use { out ->
                decoded.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }

            LoadedSketchImage(
                bitmap = decoded,
                mimeType = mime,
                sizeBytes = rawBytes.size.toLong(),
                sha256Hex = ZipAndArtifactExporter.sha256Bytes(rawBytes),
                cachedFilePath = cachedFile.absolutePath
            )
        }
    }

    fun applyTransformations(
        source: Bitmap,
        rotationDegrees: Int,
        highContrastInkMode: Boolean
    ): Bitmap {
        val rotated = if (rotationDegrees % 360 != 0) {
            val matrix = Matrix().apply { postRotate((rotationDegrees % 360).toFloat()) }
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        } else {
            source
        }

        if (!highContrastInkMode) return rotated

        val output = Bitmap.createBitmap(rotated.width, rotated.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val grayscaleMatrix = ColorMatrix().apply { setSaturation(0f) }
        val contrast = 1.45f
        val translate = (-0.5f * contrast + 0.5f) * 255f
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        grayscaleMatrix.postConcat(contrastMatrix)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(grayscaleMatrix)
        }
        canvas.drawBitmap(rotated, 0f, 0f, paint)
        return output
    }

    fun createSampleWireframeBitmap(): Bitmap {
        val bmp = Bitmap.createBitmap(540, 960, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(android.graphics.Color.WHITE)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(30, 40, 50)
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(20, 30, 40)
            textSize = 36f
            isFakeBoldText = true
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(60, 70, 80)
            textSize = 26f
        }

        // App bar box
        canvas.drawRect(30f, 30f, 510f, 120f, strokePaint)
        canvas.drawText("TaskFlow Login", 60f, 88f, textPaint)

        // Hero card
        canvas.drawRect(50f, 160f, 490f, 310f, strokePaint)
        canvas.drawText("Welcome Back", 150f, 248f, textPaint)

        // Email input
        canvas.drawRect(50f, 360f, 490f, 445f, strokePaint)
        canvas.drawText("Email Address", 75f, 412f, bodyPaint)

        // Password input
        canvas.drawRect(50f, 475f, 490f, 560f, strokePaint)
        canvas.drawText("Password", 75f, 527f, bodyPaint)

        // Primary CTA button
        canvas.drawRect(50f, 610f, 490f, 700f, strokePaint)
        canvas.drawText("Sign In", 210f, 667f, textPaint)

        // Secondary text
        canvas.drawText("Create Account", 175f, 780f, bodyPaint)

        val stream = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        return bmp
    }
}
