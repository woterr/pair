package com.wood.pair

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wood.pair.ui.theme.PairTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Proves the screenshot harness works end to end before anything depends on it.
 *
 * Robolectric's native graphics mode rasterises with the real Android graphics stack, so a
 * captured bitmap is genuine output from the real Compose tree rather than a stub. If this test
 * cannot produce a non-blank PNG then no screen comparison in this suite means anything.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    application = TestApplication::class,
    qualifiers = "w411dp-h830dp-normal-long-notround-any-420dpi-keyshidden-nonav",
)
class ScreenshotHarnessTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun nativeRenderingProducesRealPixels() {
        compose.setContent {
            PairTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF6750A4)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Pair", style = MaterialTheme.typography.displayLarge)
                }
            }
        }
        compose.waitForIdle()

        val size = compose.onRoot().fetchSemanticsNode().size
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()

        val out = File(SHOT_DIR, "harness-smoke.png")
        out.parentFile?.mkdirs()
        out.outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }

        assertTrue("root was not laid out", size.width > 0 && size.height > 0)
        assertTrue("bitmap has no area", bitmap.width > 0 && bitmap.height > 0)
        // A blank capture is the failure mode that would silently pass an existence check, so
        // the test asserts on actual pixel content.
        assertTrue(
            "captured bitmap is a single flat colour: native rendering did not run",
            countDistinctColours(bitmap) > 1,
        )
        println("screenshot: ${out.absolutePath} ${bitmap.width}x${bitmap.height}")
    }

    private fun countDistinctColours(bitmap: Bitmap): Int {
        val seen = HashSet<Int>()
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                seen.add(bitmap.getPixel(x, y))
                if (seen.size > 8) return seen.size
                x += 4
            }
            y += 4
        }
        return seen.size
    }

    private companion object {
        /** Screenshots land beside the other build outputs, not in the source tree. */
        val SHOT_DIR: String =
            System.getProperty("pair.screenshotDir") ?: "build/screenshots"
    }
}
