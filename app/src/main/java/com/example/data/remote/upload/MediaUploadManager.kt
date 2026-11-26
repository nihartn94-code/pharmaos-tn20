package com.example.data.remote.upload

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.BuildConfig
import com.example.data.local.DataStoreManager
import com.example.data.remote.config.AuthExecutionMode
import com.example.data.remote.source.WorkspaceRemoteDataSource
import com.example.data.remote.util.NetworkResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.ByteArrayOutputStream

enum class MediaUploadKind(
    val allowedMimeTypes: Set<String>,
    val maxBytes: Long,
    val label: String
) {
    IMAGE(
        allowedMimeTypes = setOf("image/jpeg", "image/jpg", "image/png", "image/webp"),
        maxBytes = 5L * 1024L * 1024L, // 5 MB
        label = "Image (JPEG, PNG, WebP up to 5 MB)"
    ),
    DOCUMENT(
        allowedMimeTypes = setOf("application/pdf", "image/jpeg", "image/jpg", "image/png"),
        maxBytes = 10L * 1024L * 1024L, // 10 MB
        label = "Document (PDF, JPEG, PNG up to 10 MB)"
    )
}

sealed interface MediaUploadState {
    data object Idle : MediaUploadState
    data class Validating(val localPreviewUri: String) : MediaUploadState
    data class Uploading(
        val localPreviewUri: String,
        val fileName: String,
        val progress: Float
    ) : MediaUploadState
    data class Success(
        val remoteUrl: String,
        val localPreviewUri: String?,
        val contentType: String,
        val sizeBytes: Long
    ) : MediaUploadState
    data class Failed(
        val message: String,
        val localPreviewUri: String?,
        val retryable: Boolean = true
    ) : MediaUploadState
    data object Cancelled : MediaUploadState
}

/**
 * End-to-end media & document upload manager for PharmaOS.
 *
 * Security & persistence rules:
 * 1. Validates MIME type and maximum byte size before uploading.
 * 2. Streams multipart upload with real-time progress reporting to POST /api/v1/media/upload.
 * 3. Persists ONLY validated remote HTTPS URLs in PRODUCTION_BACKEND_HTTP mode — raw local
 *    `content://` or `file://` URIs are strictly blocked from being sent to backend domain entities.
 * 4. Supports retry and cancellation cleanly without leaking stream handles.
 */
class MediaUploadManager(
    private val context: Context,
    private val dataStoreManager: DataStoreManager,
    private val remoteDataSource: WorkspaceRemoteDataSource
) {

    suspend fun uploadUri(
        uri: Uri,
        kind: MediaUploadKind = MediaUploadKind.IMAGE,
        onStateChanged: (MediaUploadState) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val localPreview = uri.toString()
        onStateChanged(MediaUploadState.Validating(localPreview))

        try {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri)?.lowercase()
            val (fileName, reportedSize) = queryDisplayNameAndSize(uri)

            // Read stream with hard upper bound to prevent OOM and enforce exact size limit
            val bytes = readBoundedBytes(uri, kind.maxBytes)
                ?: run {
                    val err = "File exceeds maximum allowed size (${kind.maxBytes / (1024 * 1024)} MB) for ${kind.label}."
                    onStateChanged(MediaUploadState.Failed(err, localPreview, retryable = false))
                    return@withContext Result.failure(IllegalArgumentException(err))
                }

            val actualSize = if (bytes.isNotEmpty()) bytes.size.toLong() else reportedSize
            val validation = validateMediaAsset(mimeType, actualSize, kind)
            if (validation.isFailure) {
                val msg = validation.exceptionOrNull()?.message ?: "Invalid media file."
                onStateChanged(MediaUploadState.Failed(msg, localPreview, retryable = false))
                return@withContext Result.failure(IllegalArgumentException(msg))
            }

            val effectiveMime = mimeType ?: "image/jpeg"
            val safeFileName = fileName.ifBlank {
                if (effectiveMime == "application/pdf") "document.pdf" else "upload.jpg"
            }

            val isProduction = dataStoreManager.authExecutionModeFlow.first() == AuthExecutionMode.PRODUCTION_BACKEND_HTTP
            if (!isProduction && BuildConfig.DEBUG) {
                // Local debug mode only: simulate immediate completion while retaining local preview
                onStateChanged(MediaUploadState.Uploading(localPreview, safeFileName, 0.5f))
                onStateChanged(
                    MediaUploadState.Success(
                        remoteUrl = localPreview,
                        localPreviewUri = localPreview,
                        contentType = effectiveMime,
                        sizeBytes = actualSize
                    )
                )
                return@withContext Result.success(localPreview)
            }

            onStateChanged(MediaUploadState.Uploading(localPreview, safeFileName, 0.05f))

            val body = ProgressByteArrayRequestBody(
                bytes = bytes,
                mediaType = effectiveMime.toMediaTypeOrNull(),
                onProgress = { fraction ->
                    onStateChanged(
                        MediaUploadState.Uploading(
                            localPreviewUri = localPreview,
                            fileName = safeFileName,
                            progress = fraction.coerceIn(0.05f, 0.98f)
                        )
                    )
                }
            )
            val part = MultipartBody.Part.createFormData("file", safeFileName, body)

            when (val res = remoteDataSource.uploadMediaAsset(part)) {
                is NetworkResult.Success -> {
                    val url = res.data.assetUrl.trim()
                    if (!isPersistableProductionMediaUrl(url)) {
                        val msg = "Backend returned an invalid media URL. Only HTTPS asset URLs are permitted."
                        onStateChanged(MediaUploadState.Failed(msg, localPreview, retryable = true))
                        Result.failure(IllegalStateException(msg))
                    } else {
                        onStateChanged(
                            MediaUploadState.Success(
                                remoteUrl = url,
                                localPreviewUri = localPreview,
                                contentType = res.data.contentType.ifBlank { effectiveMime },
                                sizeBytes = if (res.data.sizeBytes > 0L) res.data.sizeBytes else actualSize
                            )
                        )
                        Result.success(url)
                    }
                }
                else -> {
                    val err = res.toException()
                    onStateChanged(
                        MediaUploadState.Failed(
                            message = err.message ?: "Media upload failed. Tap retry to try again.",
                            localPreviewUri = localPreview,
                            retryable = true
                        )
                    )
                    Result.failure(err)
                }
            }
        } catch (ce: CancellationException) {
            onStateChanged(MediaUploadState.Cancelled)
            throw ce
        } catch (e: Exception) {
            val msg = e.message ?: "Unable to read or upload selected file."
            onStateChanged(MediaUploadState.Failed(msg, localPreview, retryable = true))
            Result.failure(e)
        }
    }

    private fun queryDisplayNameAndSize(uri: Uri): Pair<String, Long> {
        var name = ""
        var size = -1L
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) name = cursor.getString(nameIdx).orEmpty()
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
                }
            }
        }
        return name to size
    }

    private fun readBoundedBytes(uri: Uri, maxBytes: Long): ByteArray? {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Unable to open selected file stream.")
        input.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                total += read
                if (total > maxBytes) {
                    return null
                }
                out.write(buffer, 0, read)
            }
            return out.toByteArray()
        }
    }

    private class ProgressByteArrayRequestBody(
        private val bytes: ByteArray,
        private val mediaType: MediaType?,
        private val onProgress: (Float) -> Unit
    ) : RequestBody() {
        override fun contentType(): MediaType? = mediaType
        override fun contentLength(): Long = bytes.size.toLong()
        override fun writeTo(sink: BufferedSink) {
            val chunkSize = 8192
            var uploaded = 0
            val total = bytes.size.coerceAtLeast(1)
            while (uploaded < bytes.size) {
                val count = minOf(chunkSize, bytes.size - uploaded)
                sink.write(bytes, uploaded, count)
                uploaded += count
                onProgress(uploaded.toFloat() / total.toFloat())
            }
        }
    }

    companion object {
        fun validateMediaAsset(
            mimeType: String?,
            sizeBytes: Long,
            kind: MediaUploadKind
        ): Result<Unit> {
            val normalizedMime = mimeType?.trim()?.lowercase()
            if (normalizedMime.isNullOrBlank() || normalizedMime !in kind.allowedMimeTypes) {
                return Result.failure(
                    IllegalArgumentException(
                        "Unsupported file type (${mimeType ?: "unknown"}). Allowed: ${kind.label}."
                    )
                )
            }
            if (sizeBytes <= 0L) {
                return Result.failure(IllegalArgumentException("Selected file is empty."))
            }
            if (sizeBytes > kind.maxBytes) {
                return Result.failure(
                    IllegalArgumentException(
                        "File size exceeds ${kind.maxBytes / (1024 * 1024)} MB limit for ${kind.label}."
                    )
                )
            }
            return Result.success(Unit)
        }

        /**
         * Production persistence guard: rejects local Android `content://` or `file://` URIs
         * and enforces valid HTTPS URLs when media is attached.
         */
        fun isPersistableProductionMediaUrl(url: String?): Boolean {
            if (url.isNullOrBlank()) return true
            val trimmed = url.trim().lowercase()
            if (trimmed.startsWith("content://") || trimmed.startsWith("file://")) return false
            return trimmed.startsWith("https://")
        }
    }
}
