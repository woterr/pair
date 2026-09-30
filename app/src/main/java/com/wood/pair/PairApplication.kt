package com.wood.pair

import android.app.Application
import android.util.Log
import com.wood.pair.data.PairGraph
import com.wood.pair.notifications.LiveUpdateNotifier

/**
 * Application entry point.
 *
 * Deliberately does no work on the main thread beyond wiring: the repositories are lazy, so
 * nothing here can stall the first frame.
 */
class PairApplication : Application() {

    /** Simple service locator. The project does not need a DI framework. */
    val graph: PairGraph by lazy { PairGraph(this) }

    override fun onCreate() {
        super.onCreate()
        installDebugPeer()
        // Notification channels must exist before the first Live Update is posted.
        LiveUpdateNotifier.ensureChannels(this)
        // Registers the FCM token with the database once a session exists.
        graph.fcmTokenRepository.startTokenSync()
    }

    /**
     * Installs the debug-only "second device" so the whole product can be tested on a single
     * phone.
     *
     * `PeerBootstrap` lives in the `debug` source set and does not exist in release builds, so
     * it is loaded by name. `BuildConfig.DEBUG` is a compile-time constant, which means R8
     * removes this call — and the reference to the class — from release builds entirely.
     */
    private fun installDebugPeer() {
        if (!BuildConfig.DEBUG) return
        runCatching {
            val type = Class.forName("com.wood.pair.debug.PeerBootstrap")
            type.getMethod("install", Application::class.java).invoke(null, this)
        }.onFailure { error ->
            // Never fatal: a missing debug harness must not stop the app starting.
            Log.w("PairApplication", "Debug peer device unavailable", error)
        }
    }
}
