package com.wood.pair.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.wood.pair.data.model.LocationRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Outcome of a wallpaper change. */
sealed interface WallpaperResult {
    data object Applied : WallpaperResult
    data class Rejected(val reason: String) : WallpaperResult
}

/**
 * Local wallpaper storage and application.
 *
 * Images chosen by the user are copied into app-internal storage and referenced from there.
 * That means:
 *  - the rule keeps working after the original photo is deleted from the gallery;
 *  - nothing personal is uploaded anywhere;
 *  - Pair never holds a content URI permission it would have to re-acquire.
 *
 * Only Android's supported [android.app.WallpaperManager] API is used. No root, no
 * accessibility service, and no foreground service is involved.
 */
class WallpaperStore(context: Context) {

    private val appContext = context.applicationContext
    private val wallpaperDir: File by lazy {
        File(appContext.filesDir, WALLPAPER_DIR).also { it.mkdirs() }
    }

    /**
     * Copies the picked image into app storage and returns the internal reference to store
     * on the rule, or `null` if the image could not be read.
     */
    suspend fun importImage(source: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(wallpaperDir, "${UUID.randomUUID()}.jpg")
            appContext.contentResolver.openInputStream(source)?.use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            } ?: return@runCatching null
            target.absolutePath
        }.onFailure { Log.w(TAG, "Could not import wallpaper from $source", it) }
            .getOrNull()
    }

    /** True when [reference] still resolves to a readable file. */
    suspend fun exists(reference: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { File(reference).let { it.isFile && it.length() > 0 } }.getOrDefault(false)
    }

    /** Loads a wallpaper for preview, downsampled so a large photo cannot exhaust memory. */
    suspend fun loadPreview(reference: String, maxDimensionPx: Int = 1024): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = File(reference)
                if (!file.isFile) return@runCatching null

                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                val longest = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)

                val options = BitmapFactory.Options().apply {
                    inSampleSize = generateSequence(1) { it * 2 }
                        .first { longest / it <= maxDimensionPx }
                        .coerceAtLeast(1)
                }
                BitmapFactory.decodeFile(file.absolutePath, options)
            }.onFailure { Log.w(TAG, "Could not decode preview for $reference", it) }
                .getOrNull()
        }

    /**
     * Sets the system wallpaper from a stored reference.
     *
     * A [SecurityException] is reported as a rejection rather than a crash: some OEM builds
     * and managed devices restrict wallpaper changes, and the user deserves to be told that
     * instead of watching the app die.
     */
    suspend fun applySystem(reference: String): WallpaperResult = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = loadPreview(reference, maxDimensionPx = MAX_WALLPAPER_PX)
                ?: return@runCatching WallpaperResult.Rejected("The image could not be read")
            val manager = android.app.WallpaperManager.getInstance(appContext)
            manager.setBitmap(
                bitmap,
                null,
                true,
                android.app.WallpaperManager.FLAG_SYSTEM,
            )
            WallpaperResult.Applied
        }.getOrElse { error ->
            val message = if (error is SecurityException) {
                "This device blocked the wallpaper change"
            } else {
                error.message ?: "Wallpaper could not be set"
            }
            Log.w(TAG, "Wallpaper change failed", error)
            WallpaperResult.Rejected(message)
        }
    }

    /** Deletes a stored image. Used when the rule that referenced it is deleted. */
    suspend fun discard(reference: String) = withContext(Dispatchers.IO) {
        runCatching { File(reference).takeIf { it.isFile }?.delete() }
        Unit
    }

    /** True when [rule] has everything it needs to be armed. */
    suspend fun isReady(rule: LocationRule): Boolean = exists(rule.wallpaperUri)

    private companion object {
        const val TAG = "WallpaperStore"
        const val WALLPAPER_DIR = "wallpapers"

        /**
         * Wallpapers are scaled by the system anyway, so a very large bitmap only wastes
         * memory here.
         */
        const val MAX_WALLPAPER_PX = 4096
    }
}
