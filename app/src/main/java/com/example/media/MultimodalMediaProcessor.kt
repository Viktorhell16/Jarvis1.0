package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.core.content.FileProvider
import com.example.telemetry.JarvisCrashReporter
import com.example.telemetry.JarvisPerformanceTracker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

enum class MediaAttachmentType {
    PHOTO,
    VIDEO
}

data class EncodedMediaFrame(
    val base64Jpeg: String,
    val label: String? = null
)

data class PendingMediaAttachment(
    val id: String = UUID.randomUUID().toString(),
    val type: MediaAttachmentType,
    val displayName: String,
    val badgeText: String,
    val detailsText: String,
    val previewBitmap: Bitmap? = null,
    val frames: List<EncodedMediaFrame> = emptyList(),
    val isProcessing: Boolean = false
)

data class MessageMediaPayload(
    val type: MediaAttachmentType,
    val summaryLabel: String,
    val frames: List<EncodedMediaFrame>,
    val previewBitmaps: List<Bitmap> = emptyList()
)

object MultimodalMediaProcessor {

    private const val MAX_PHOTO_DIMENSION = 1024
    private const val MAX_VIDEO_FRAME_DIMENSION = 768
    private const val THUMBNAIL_DIMENSION = 240
    private const val JPEG_QUALITY = 85

    fun createCameraPhotoUri(context: Context): Uri {
        val dir = File(context.cacheDir, "camera_captures").apply { mkdirs() }
        val photoFile = File(dir, "jarvis_cam_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
    }

    suspend fun processImageUri(context: Context, uri: Uri): Result<PendingMediaAttachment> =
        withContext(Dispatchers.IO) {
            val trace = JarvisPerformanceTracker.startMultimodalMediaEncodeTrace("photo")
            var extractedFrames = 0L
            try {
                val displayName = resolveDisplayName(context, uri) ?: "Фото"
                val orientation = readExifOrientation(context, uri)
                val decoded = decodeSampledBitmap(context, uri, MAX_PHOTO_DIMENSION)
                    ?: return@withContext Result.failure(IllegalArgumentException("Не удалось декодировать изображение"))
                val rotated = applyExifOrientation(decoded, orientation)
                val scaled = scaleBitmap(rotated, MAX_PHOTO_DIMENSION)
                if (rotated !== decoded) decoded.recycle()
                if (scaled !== rotated) rotated.recycle()

                val base64 = encodeJpegBase64(scaled, JPEG_QUALITY)
                val thumb = scaleBitmap(scaled, THUMBNAIL_DIMENSION)
                val details = "${scaled.width}×${scaled.height}"
                if (thumb !== scaled) scaled.recycle()

                extractedFrames = 1L
                Result.success(
                    PendingMediaAttachment(
                        type = MediaAttachmentType.PHOTO,
                        displayName = displayName,
                        badgeText = "Фото",
                        detailsText = details,
                        previewBitmap = thumb,
                        frames = listOf(EncodedMediaFrame(base64Jpeg = base64, label = null)),
                        isProcessing = false
                    )
                )
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                JarvisCrashReporter.recordNonFatal(t, "media_decode_image")
                Result.failure(if (t is Exception) t else RuntimeException("Ошибка памяти при декодировании фото", t))
            } finally {
                trace.putMetric("extracted_frames_count", extractedFrames)
                trace.stop()
            }
        }

    suspend fun processVideoUri(context: Context, uri: Uri): Result<PendingMediaAttachment> =
        withContext(Dispatchers.IO) {
            val trace = JarvisPerformanceTracker.startMultimodalMediaEncodeTrace("video")
            var extractedFrames = 0L
            val retriever = MediaMetadataRetriever()
            try {
                val displayName = resolveDisplayName(context, uri) ?: "Видео"
                retriever.setDataSource(context, uri)

                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
                val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)

                val frameCount = when {
                    durationMs <= 10_000L -> 2
                    durationMs <= 30_000L -> 3
                    else -> 4
                }

                val frames = mutableListOf<EncodedMediaFrame>()
                var thumbBitmap: Bitmap? = null

                val effectiveDurationMs = max(durationMs, 1000L)
                for (i in 0 until frameCount) {
                    val fraction = if (frameCount <= 1) 0.5 else (i + 0.5) / frameCount.toDouble()
                    val timeMs = (effectiveDurationMs * fraction).toLong().coerceIn(0L, effectiveDurationMs)
                    val timeUs = timeMs * 1000L
                    val rawFrame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.getFrameAtTime(timeUs)
                    if (rawFrame != null) {
                        val scaled = scaleBitmap(rawFrame, MAX_VIDEO_FRAME_DIMENSION)
                        if (scaled !== rawFrame) rawFrame.recycle()
                        if (thumbBitmap == null) {
                            thumbBitmap = scaleBitmap(scaled, THUMBNAIL_DIMENSION)
                        }
                        val b64 = encodeJpegBase64(scaled, 82)
                        if (scaled !== thumbBitmap) scaled.recycle()
                        val tsLabel = "[Кадр ${formatDuration(timeMs)}]"
                        frames.add(EncodedMediaFrame(base64Jpeg = b64, label = tsLabel))
                    }
                }

                if (frames.isEmpty()) {
                    return@withContext Result.failure(IllegalStateException("Не удалось извлечь кадры из видео"))
                }

                extractedFrames = frames.size.toLong()
                val resTag = if (!widthStr.isNullOrBlank() && !heightStr.isNullOrBlank()) {
                    " • ${widthStr}×${heightStr}"
                } else ""
                val details = "${formatDuration(durationMs)}$resTag • ${frames.size} кадров"

                Result.success(
                    PendingMediaAttachment(
                        type = MediaAttachmentType.VIDEO,
                        displayName = displayName,
                        badgeText = "Видео",
                        detailsText = details,
                        previewBitmap = thumbBitmap,
                        frames = frames,
                        isProcessing = false
                    )
                )
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                JarvisCrashReporter.recordNonFatal(t, "media_decode_video")
                Result.failure(if (t is Exception) t else RuntimeException("Ошибка памяти при декодировании видео", t))
            } finally {
                trace.putMetric("extracted_frames_count", extractedFrames)
                trace.stop()
                runCatching { retriever.release() }
            }
        }

    fun decodeBase64ToBitmap(base64Jpeg: String): Bitmap? {
        return try {
            val bytes = Base64.decode(base64Jpeg, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    private fun readExifOrientation(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            else -> return bitmap
        }
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (_: Exception) {
            bitmap
        }
    }

    private fun decodeSampledBitmap(context: Context, uri: Uri, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        val maxSide = max(bounds.outWidth, bounds.outHeight)
        var sampleSize = 1
        if (maxSide > maxDim) {
            while (maxSide / (sampleSize * 2) >= maxDim) {
                sampleSize *= 2
            }
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, opts)
        }
    }

    private fun scaleBitmap(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val longest = max(w, h)
        if (longest <= maxDim || longest <= 0) return bitmap
        val ratio = maxDim.toFloat() / longest.toFloat()
        val newW = (w * ratio).roundToInt().coerceAtLeast(1)
        val newH = (h * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    private fun encodeJpegBase64(bitmap: Bitmap, quality: Int): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && idx >= 0) c.getString(idx) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = (ms / 1000L).coerceAtLeast(0L)
        val minutes = totalSec / 60L
        val seconds = totalSec % 60L
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
