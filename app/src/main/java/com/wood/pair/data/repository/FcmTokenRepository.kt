package com.wood.pair.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import com.wood.pair.core.launchSafely
import com.wood.pair.data.remote.await
import com.wood.pair.data.remote.firebaseWithTimeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Keeps the current FCM registration token in the Realtime Database so the Cloud Function
 * can address this device.
 *
 * Rules notes:
 *  - Only the signed-in user may write their own token; the deployed rules enforce that on
 *    `users/<uid>`, so no client can overwrite somebody else's token.
 *  - The client never sends a message. It has no server credential and cannot; sending is
 *    the Cloud Function's job using the Admin SDK.
 */
class FcmTokenRepository(
    private val messaging: FirebaseMessaging,
    private val database: FirebaseDatabase,
    private val auth: FirebaseAuth,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Fetches the current token and stores it once a session exists.
     *
     * Failures are logged and swallowed rather than propagated. This runs detached from the
     * UI, and a `launch` whose exception is never caught reaches the thread's uncaught
     * exception handler — which crashes the process. A missing token degrades remote wake-up
     * for this install, but it must never be able to take the app down.
     */
    fun startTokenSync() {
        scope.launchSafely("FCM token registration") { syncToken() }
    }

    suspend fun syncToken(): Boolean = firebaseWithTimeout(what = "FCM token") {
        val uid = auth.currentUser?.uid ?: return@firebaseWithTimeout false
        val token = messaging.token.await()
        writeToken(uid, token)
        true
    }

    /**
     * Persists a token obtained from `FirebaseMessagingService.onNewToken`.
     *
     * This is the rotation path: a token can change at any time, and the previous one must
     * be replaced or pushes would go to a dead registration.
     */
    suspend fun onNewToken(token: String) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            // A token can arrive before a session exists. Retry once a session is available.
            Log.i(TAG, "Token rotation arrived before sign-in; deferring")
            return
        }
        runCatching { writeToken(uid, token) }
            .onFailure { Log.w(TAG, "Could not store rotated token", it) }
    }

    private suspend fun writeToken(uid: String, token: String) {
        // `reference` with no path is the database root; there is no `root` property on
        // FirebaseDatabase in the current SDK.
        database.reference.child(Paths.user(uid)).child("fcmToken")
            .setValue(token)
            .await()
        Log.i(TAG, "Stored FCM token for $uid")
    }

    private companion object {
        const val TAG = "FcmTokenRepository"
    }
}
