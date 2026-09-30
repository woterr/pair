package com.wood.pair.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.wood.pair.data.model.LocationRule
import com.wood.pair.data.model.WallpaperTarget
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
     * Sets a wallpaper from a stored reference, on the surfaces [target] names.
     *
     * A [SecurityException] is reported as a rejection rather than a crash: some OEM builds
     * and managed devices restrict wallpaper changes, and the user deserves to be told that
     * instead of watching the app die.
     */
    suspend fun apply(
        reference: String,
        target: WallpaperTarget = WallpaperTarget.Home,
    ): WallpaperResult = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = loadPreview(reference, maxDimensionPx = MAX_WALLPAPER_PX)
                ?: return@runCatching WallpaperResult.Rejected("The image could not be read")
            val manager = android.app.WallpaperManager.getInstance(appContext)
            manager.setBitmap(
                cropToWallpaperAspect(bitmap, wallpaperSize(manager, target)),
                null,
                true,
                target.flags,
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

    /** The old name, kept so the call sites that do not choose a surface still read clearly. */
    suspend fun applySystem(reference: String): WallpaperResult =
        apply(reference, WallpaperTarget.Home)

    /**
     * The pixel size the target surface actually wants.
     *
     * Home and lock wallpapers are not the same size on any phone - the lock one is taller,
     * because it has to clear the clock and the shortcut row - so a single hard-coded size would
     * be wrong for one of them on every device.
     *
     * [WallpaperManager.getDesiredMinimumWidth] / `Height` are the system's own advice for the
     * surface being set. They are zero on some builds, so the display size is the fallback rather
     * than the answer.
     */
    private fun wallpaperSize(
        manager: android.app.WallpaperManager,
        target: WallpaperTarget,
    ): Pair<Int, Int> {
        val width = manager.desiredMinimumWidth
        val height = manager.desiredMinimumHeight
        if (width > 0 && height > 0) return width to height
        val metrics = appContext.resources.displayMetrics
        return metrics.widthPixels to metrics.heightPixels
    }

    /**
     * Centre-crops [bitmap] to [target]'s aspect ratio.
     *
     * ## Why the crop is done here rather than left to the system
     *
     * `WallpaperManager.setBitmap` scales the image to cover the surface, and on a mismatch
     * between the image's aspect and the surface's, "cover" is either a stretch or a letterbox
     * depending on the build. Both were reported: a portrait photo on a tall lock screen came out
     * squeezed, and a wide photo on the same screen came out with black bars down the sides.
     *
     * Neither is a defect of the image. The only correct answer for "fill this surface" is to
     * keep the image's proportions and throw away the overflow, and the only place that can be
     * decided with knowledge of *this* device's surface is here. The result is exactly
     * `ContentScale.Crop`: no distortion, and no gaps, because the crop fills the frame by
     * construction.
     *
     * Returns the original bitmap untouched when its aspect already matches, so the common case
     * costs nothing and the common case is the one that is already right.
     */
    private fun cropToWallpaperAspect(bitmap: Bitmap, target: Pair<Int, Int>): Bitmap {
        val (targetWidth, targetHeight) = target
        if (targetWidth <= 0 || targetHeight <= 0) return bitmap
        if (bitmap.width <= 0 || bitmap.height <= 0) return bitmap

        val sourceRatio = bitmap.width.toFloat() / bitmap.height
        val targetRatio = targetWidth.toFloat() / targetHeight
        if (kotlin.math.abs(sourceRatio - targetRatio) < ASPECT_TOLERANCE) return bitmap

        return if (sourceRatio > targetRatio) {
            // Too wide: keep full height, take a narrower slice from the middle.
            val cropWidth = (bitmap.height * targetRatio).toInt().coerceIn(1, bitmap.width)
            val left = (bitmap.width - cropWidth) / 2
            Bitmap.createBitmap(bitmap, left, 0, cropWidth, bitmap.height)
        } else {
            // Too tall: keep full width, take a shorter slice from the middle.
            val cropHeight = (bitmap.width / targetRatio).toInt().coerceIn(1, bitmap.height)
            val top = (bitmap.height - cropHeight) / 2
            Bitmap.createBitmap(bitmap, 0, top, bitmap.width, cropHeight)
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

        /**
                 * How close two aspect ratios must be before the crop is skipped.
                 *
                 * A tenth of a percent. Loose enough that ordinary camera output is left alone, tight
                 * enough that a genuinely different frame - a 4:3 photo on a 19.5:9 screen - is cropped
                 * rather than squeezed.
                 */
                const val ASPECT_TOLERANCE = 0.001f
    }
}
