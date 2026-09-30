package com.wood.pair.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.wood.pair.data.remote.FirebaseProvider
import com.wood.pair.data.remote.await
import com.wood.pair.data.remote.firebaseWithTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What the app knows about the current Firebase session. */
sealed interface AuthState {
    /** Not yet determined. Resolved synchronously in [AuthRepository]'s constructor. */
    data object Unknown : AuthState

    /** No user yet. Pair creates one anonymously on demand. */
    data object SignedOut : AuthState

    data class SignedIn(val uid: String) : AuthState
}

/**
 * Owns the anonymous Firebase session.
 *
 * There is no email, password, phone number or Google account in Pair. The anonymous UID
 * *is* the identity; the human-readable name is separate, held in DataStore and mirrored
 * into the database.
 */
class AuthRepository(
    private val auth: FirebaseAuth = FirebaseProvider.auth,
) {

    private val _state = MutableStateFlow<AuthState>(AuthState.Unknown)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    /**
     * Serialises sign-in so two callers racing at startup (the UI and a background FCM token
     * refresh, say) cannot start two competing `signInAnonymously` calls.
     */
    private val signInMutex = Mutex()

    private val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        _state.value = firebaseAuth.currentUser.toAuthState()
    }

    init {
        auth.addAuthStateListener(listener)
        // Seed synchronously so the very first frame already has a definite state instead of
        // waiting for a listener callback.
        _state.value = auth.currentUser.toAuthState()
    }

    private fun FirebaseUser?.toAuthState(): AuthState =
        this?.let { AuthState.SignedIn(it.uid) } ?: AuthState.SignedOut

    /**
     * Returns the current UID, signing in anonymously if necessary.
     *
     * Bounded by a timeout: if Auth cannot reach the network this throws instead of
     * suspending forever, so the UI can show a real error and offer a retry.
     */
    suspend fun ensureSignedIn(): String = firebaseWithTimeout(what = "sign-in") {
        auth.currentUser?.let { return@firebaseWithTimeout it.uid }
        signInMutex.withLock {
            // Re-check inside the lock: another caller may have just succeeded.
            auth.currentUser?.let { return@withLock it.uid }
            auth.signInAnonymously().await().user!!.uid
        }
    }

    /** The current UID if already signed in, without triggering a network sign-in. */
    fun currentUidOrNull(): String? = auth.currentUser?.uid

    /** Waits for the session state to be known, without creating a session. */
    suspend fun awaitKnownSession(): AuthState = state.first { it != AuthState.Unknown }

    /**
     * Drops the anonymous session.
     *
     * Pair keeps no cloud-side profile beyond a display name, so nothing is worth preserving
     * server-side; the caller clears local session state. `signOut` is synchronous in the
     * current SDK, but it still triggers a network round trip internally, so the result is
     * observed via [state] rather than awaited.
     */
    suspend fun signOut(): AuthState {
        auth.signOut()
        return state.first { it == AuthState.SignedOut }
    }
}
