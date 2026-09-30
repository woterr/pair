package com.wood.pair

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.wood.pair.ui.theme.Appearance
import com.wood.pair.ui.theme.PairTheme
import com.wood.pair.ui.theme.ThemeMode
import org.junit.Assert.assertTrue
import org.junit.Rule
import java.io.File

/**
 * Shared plumbing for the screen-rendering tests.
 *
 * Why this exists rather than a device: the Android emulator on this machine has no hardware
 * acceleration available and cannot be granted it without elevation, so no AVD will boot.
 * Robolectric's native graphics mode rasterises the real Compose tree with the real Android
 * graphics stack instead, which produces genuine pixels on the JVM — at the same 1080x2400 the
 * comps are drawn at, so a rendered screen can be laid over a comp and compared directly.
 *
 * That is a real limitation and worth stating plainly: it verifies layout, colour, type and
 * artwork, and it runs the actual composables — but it does not exercise the platform's own
 * behaviour (geofence registration, wallpaper application, Live Update promotion), which still
 * needs a device or a booted emulator.
 */
object ScreenShots {

    /**
     * Where rendered screens land.
     *
     * Under `build/` rather than in the source tree, so a render is never mistaken for a source
     * file and never ends up in a commit by accident.
     */
    val outputDir: File = File("build/screenshots")

    /** 1080x2400 at 420dpi: exactly the comps' own dimensions. */
    const val WIDTH_DP = 411
    const val HEIGHT_DP = 830

    fun render(
        compose: ComposeContentTestRule,
        name: String,
        appearance: Appearance = Appearance(themeMode = ThemeMode.Light),
        dark: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            // Rendered on the comps' own palette so a layout comparison is not swamped by a
            // colour difference. The app itself resolves this from Material You; see
            // [BaselinePalette] for why the baseline is a test fixture and not a brand colour.
            PairTheme(
                appearance = appearance,
                colorSchemeOverride = if (dark) BaselinePalette.dark else BaselinePalette.light,
            ) {
                // The same root background the real app paints, so a screen that forgets to fill
                // its own surface shows up here rather than as a transparent hole.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Box(modifier = Modifier.fillMaxSize()) { content() }
                }
            }
        }
        compose.waitForIdle()

        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val file = File(outputDir, "$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        assertTrue("$name rendered no pixels", bitmap.width > 0 && bitmap.height > 0)
        assertTrue(
            "$name rendered a single flat colour, so nothing was drawn",
            distinctColours(bitmap) > 4,
        )
        println("rendered $name -> ${file.absolutePath} (${bitmap.width}x${bitmap.height})")
    }

    /**
     * A cheap "did anything actually draw" check.
     *
     * A blank capture is the failure mode that would otherwise pass silently, and on a theme
     * regression every pixel can legitimately become one colour.
     */
    fun distinctColours(bitmap: Bitmap, limit: Int = 24): Int {
        val seen = HashSet<Int>()
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                seen.add(bitmap.getPixel(x, y))
                if (seen.size >= limit) return seen.size
                x += 6
            }
            y += 6
        }
        return seen.size
    }
}

/** Base for the per-screen render tests. */
abstract class ScreenRenderTest {
    @get:Rule
    val compose = createComposeRule()
}
