package com.wood.pair.debug

import kotlinx.coroutines.flow.StateFlow

/**
 * State of the debug-only second device.
 *
 * This exists so the whole product can be exercised on a single phone without faking
 * anything. The "second device" holds a real second [android.app.Application]-scoped
 * Firebase identity (a second anonymous account and a second FCM registration) and talks to
 * the real Realtime Database, so the claim atomicity, the security rules, the debounce, the
 * listener and the FCM round trip are all genuinely exercised.
 */
data class PeerUiState(
    val isReady: Boolean = false,
    val isSignedIn: Boolean = false,
    val uid: String? = null,
    val displayName: String = "",
    val roomId: String? = null,
    val isBusy: Boolean = false,
    val message: String = "",
    val lastError: String? = null,
    /**
     * Whether the peer also posts a Live Update of its own on this phone.
     *
     * Off by default, and that default is the fix rather than a convenience.
     *
     * The peer's notification and the app's notification are *the same message*. Once the peer
     * joins the app's room, the app's Live Update already carries exactly what the peer typed —
     * that is the whole product. Posting a second notification saying the same words from a
     * second channel made every incoming message look like two events, which is precisely the
     * bug that reads as "the message arrived twice".
     *
     * The capability is kept, because proving the peer's *own* Live Update works is a different
     * question from proving the app receives one, and you should not have to give up the first
     * to test the second. It is a switch rather than a deletion.
     */
    val postsOwnLiveUpdate: Boolean = false,
) {
    /** True once the peer is a member of a room and can drive the live text. */
    val isPaired: Boolean get() = roomId != null
}

/**
 * Contract for the debug second device.
 *
 * Declared in `main` so the Settings screen can show the section without depending on
 * `debug`, and implemented in the `debug` source set so it is absent from release builds
 * entirely.
 */
interface PeerDeviceController {
    val state: StateFlow<PeerUiState>

    /** Creates the second identity. Safe to call repeatedly. */
    fun start()

    /** Signs the second identity in anonymously. */
    fun signIn(name: String)

    /**
     * Claims the second slot in [roomId] using the same transaction the real app uses, so a
     * full room is rejected exactly as it would be for any other stranger.
     */
    fun join(roomId: String, name: String)

    /** Writes the peer's live text, going through the same debounced path as the app. */
    fun sendLiveText(roomId: String, text: String)

    /**
     * Turns the peer's own Live Update on or off, and withdraws it if it is currently posted.
     *
     * [postLiveUpdate] is a no-op while this is off, so the Send button does not need to know
     * about it.
     */
    fun setPostsOwnLiveUpdate(enabled: Boolean)

    /**
     * Posts the peer's own Live Update, so both devices' notifications are visible at once.
     *
     * Only does anything when [setPostsOwnLiveUpdate] has been turned on — see
     * [PeerUiState.postsOwnLiveUpdate] for why that is off by default.
     */
    fun postLiveUpdate(roomId: String, text: String)

    /** Leaves the room. */
    fun leave()

    /** Drops the second identity so a fresh peer can be created. */
    fun reset()
}

/** Holds the debug implementation, installed reflectively by debug builds only. */
object PeerDevices {
    var controller: PeerDeviceController? = null
        internal set

    val isAvailable: Boolean get() = controller != null
}
