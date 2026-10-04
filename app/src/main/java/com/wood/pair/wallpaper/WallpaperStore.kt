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
                prepareForSurface(bitmap),
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
     * Returns [bitmap] untouched.
     *
     * ## Why there is no crop here any more
     *
     * This used to centre-crop the image to the surface's aspect ratio, on the reasoning that
     * "fill this surface, never distort it" means throw away the overflow. The reasoning was
     * sound and the outcome was the opposite of what it promised, because it cropped to the wrong
     * ratio — see [wallpaperSize] — and the system then scaled what survived into the real one.
     * The user saw an image "stretched and zoomed into": the zoom was this crop, and the stretch
     * was the system correcting the mismatch.
     *
     * Cropping is also the wrong tool for the request. "Not stretched" and "not zoomed" cannot
     * both be achieved by cropping, because cropping *is* zooming: on a portrait photo and a tall
     * screen, filling the frame by discarding overflow throws away most of the picture. The only
     * way to honour both is to hand over an image that has not been distorted or cropped at all
     * and let the platform decide how to fit it.
     *
     * So the image goes across at its own aspect ratio, and the system applies its own scaling.
     * That is the one party here with real knowledge of the surface, and it is the only place
     * where "how should this image fill this particular screen" can be answered correctly.
     *
     * The honest limit: whether the platform's own fit produces bars rather than a full bleed is
     * the platform's decision and varies by build and by launcher. What Pair can guarantee is
     * that it is not the thing distorting the image.
     */
    private fun prepareForSurface(bitmap: Bitmap): Bitmap = bitmap

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
