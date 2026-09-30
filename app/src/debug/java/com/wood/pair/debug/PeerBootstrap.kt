package com.wood.pair.debug

import android.app.Application

/**
 * Installs the debug second device.
 *
 * Loaded reflectively by [com.wood.pair.PairApplication] in debug builds only. The reflective
 * call is deliberate: `main` cannot reference a class that only exists in the `debug` source
 * set, and because `BuildConfig.DEBUG` is a compile-time constant the whole call is removed
 * from release builds by R8.
 */
object PeerBootstrap {

    /**
     * Marked `@JvmStatic` so the reflective call in `main` can pass a null receiver.
     *
     * Without it, `install` is an instance method on the singleton and `Method.invoke(null, …)`
     * throws a NullPointerException — which the caller swallows, leaving the harness silently
     * absent.
     */
    @JvmStatic
    fun install(application: Application) {
        PeerDevices.controller = PeerDevice(application)
    }
}
