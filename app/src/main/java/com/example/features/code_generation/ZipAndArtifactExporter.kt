package com.example.features.code_generation

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ExportedArchiveResult(
    val file: File,
    val sizeBytes: Long,
    val sha256Hex: String
)

object ZipAndArtifactExporter {

    private val downloadHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    fun sha256Bytes(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    fun sha256File(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun sanitizeFileName(raw: String, extension: String): String {
        val base = raw.lowercase()
            .replace(Regex("[^a-z0-9_\\-]+"), "_")
            .trim('_')
            .ifBlank { "sketchtoapp_export" }
            .take(48)
        val ext = extension.trimStart('.')
        return "$base.$ext"
    }

    suspend fun createFlutterProjectZip(
        context: Context,
        project: GeneratedFlutterProject
    ): Result<ExportedArchiveResult> = withContext(Dispatchers.IO) {
        runCatching {
            require(project.isValid && project.files.isNotEmpty()) {
                "Cannot export ZIP: Flutter project failed validation or contains zero files."
            }
            val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val safeZipName = sanitizeFileName("${project.projectSlug}_flutter_src", "zip")
            val outZip = File(exportsDir, safeZipName)

            ZipOutputStream(BufferedOutputStream(FileOutputStream(outZip))).use { zos ->
                for (sourceFile in project.files) {
                    require(FlutterProjectGenerator.isSafeRelativePath(sourceFile.relativePath)) {
                        "Unsafe path rejected during ZIP archive packaging: ${sourceFile.relativePath}"
                    }
                    val entryPath = "${project.projectSlug}/${sourceFile.relativePath}"
                    val entry = ZipEntry(entryPath)
                    zos.putNextEntry(entry)
                    val bytes = sourceFile.content.toByteArray(Charsets.UTF_8)
                    zos.write(bytes)
                    zos.closeEntry()
                }
            }

            require(outZip.exists() && outZip.length() > 0L) {
                "Generated ZIP file is empty."
            }
            val checksum = sha256File(outZip)
            ExportedArchiveResult(
                file = outZip,
                sizeBytes = outZip.length(),
                sha256Hex = checksum
            )
        }
    }

    suspend fun copyFileToDocumentUri(
        context: Context,
        sourceFile: File,
        destinationUri: Uri
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            var totalCopied = 0L
            context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                sourceFile.inputStream().buffered().use { input ->
                    totalCopied = input.copyTo(output)
                }
            } ?: error("Unable to open selected document output stream.")
            totalCopied
        }
    }

    fun shareExportedFile(
        context: Context,
        file: File,
        mimeType: String,
        chooserTitle: String
    ): Result<Unit> = runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    suspend fun downloadVerifiedArtifact(
        context: Context,
        downloadUrl: String,
        bearerIdToken: String?,
        safeFileName: String,
        expectedSha256: String,
        onProgress: (Int) -> Unit
    ): Result<ExportedArchiveResult> = withContext(Dispatchers.IO) {
        runCatching {
            require(downloadUrl.startsWith("https://") || downloadUrl.startsWith("http://10.0.2.2")) {
                "Artifact download requires a valid HTTPS endpoint (or local emulator bridge 10.0.2.2)."
            }
            val reqBuilder = Request.Builder().url(downloadUrl)
            if (!bearerIdToken.isNullOrBlank()) {
                reqBuilder.header("Authorization", "Bearer $bearerIdToken")
            }

            val artifactsDir = File(context.filesDir, "artifacts").apply { mkdirs() }
            val outFile = File(artifactsDir, sanitizeFileName(safeFileName.substringBeforeLast('.'), "apk"))

            downloadHttpClient.newCall(reqBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Artifact download failed with HTTP ${response.code}")
                }
                val body = response.body ?: error("Empty artifact response body")
                val totalLength = body.contentLength()
                var bytesRead = 0L

                FileOutputStream(outFile).buffered().use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var count: Int
                        while (input.read(buffer).also { count = it } != -1) {
                            out.write(buffer, 0, count)
                            bytesRead += count
                            if (totalLength > 0) {
                                onProgress(((bytesRead * 100L) / totalLength).toInt().coerceIn(0, 100))
                            }
                        }
                    }
                }
            }

            require(outFile.exists() && outFile.length() > 1024L) {
                "Downloaded APK is missing or invalidly small."
            }
            val actualSha = sha256File(outFile)
            if (expectedSha256.isNotBlank() && !expectedSha256.equals(actualSha, ignoreCase = true)) {
                outFile.delete()
                error("APK SHA-256 checksum mismatch! Expected $expectedSha256, got $actualSha.")
            }
            onProgress(100)
            ExportedArchiveResult(
                file = outFile,
                sizeBytes = outFile.length(),
                sha256Hex = actualSha
            )
        }
    }
}
