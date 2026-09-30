package com.wood.pair

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.wood.pair.ui.PairApp
import com.wood.pair.ui.theme.Appearance
import com.wood.pair.ui.theme.PairTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * The single activity.
 *
 * Edge to edge, with the Compose tree drawing behind the system bars and each screen handling
 * its own insets. Navigation is a Compose `NavHost` rather than several activities, so the
 * Live Update deep link has a single, predictable entry point.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val graph = (application as PairApplication).graph

        // Hold the splash only until the persisted preferences have produced a first value, so
        // the app never shows a flash of the wrong theme before applying the user's choice.
        val contentReady = MutableStateFlow(false)
        splashScreen.setKeepOnScreenCondition { !contentReady.value }

        val initialRoomId = intent.roomIdFromIntent()

        setContent {
            val localState by graph.preferences.state.collectAsStateWithLifecycle(initialValue = null)
            val appearance: Appearance = localState?.appearance ?: Appearance()

            PairTheme(appearance = appearance) {
                LaunchedEffect(Unit) { contentReady.value = true }
                PairApp(
                    graph = graph,
                    deepLinkRoomId = initialRoomId,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // A Live Update tap can arrive while Pair is already running; the NavHost observes the
        // activity's current intent, so re-publishing it is enough to navigate.
        val roomId = intent.roomIdFromIntent()
        if (roomId != null) {
            lifecycleScope.launch { pendingDeepLinkRoomId.value = roomId }
        }
    }

    /** Room ID delivered by a Live Update tap, if any. */
    private val pendingDeepLinkRoomId = MutableStateFlow<String?>(null)

    private fun Intent?.roomIdFromIntent(): String? {
        val data: Uri = this?.data ?: return null
        if (data.scheme != DEEP_LINK_SCHEME || data.host != DEEP_LINK_HOST) return null
        val roomId = data.lastPathSegment?.takeIf { it.isNotBlank() } ?: return null
        return roomId.takeIf { com.wood.pair.data.model.RoomId.isValid(it) }
    }

    private companion object {
        const val DEEP_LINK_SCHEME = "pair"
        const val DEEP_LINK_HOST = "room"
    }
}
