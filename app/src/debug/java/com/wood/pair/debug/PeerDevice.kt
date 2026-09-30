package com.wood.pair.debug

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import com.wood.pair.core.launchSafely
import com.wood.pair.data.model.Room
import com.wood.pair.data.remote.FirebaseProvider
import com.wood.pair.data.remote.await
import com.wood.pair.data.remote.awaitTransaction
import com.wood.pair.data.remote.isAbsent
import com.wood.pair.data.remote.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/**
 * A genuine second Pair installation, living inside the same APK.
 *
 * This is *not* a mock. It creates a second [FirebaseApp] under a different name, which gives
 * it its own anonymous Auth account, its own Realtime Database view and its own FCM
 * registration. So when the peer claims the second slot it competes through exactly the same
 * transaction and the same security rules as a person on another phone would, and when it
 * changes the live text the Cloud Function genuinely pushes to the other identity.
 *
 * Only the debug build includes this.
 */
class PeerDevice(private val application: Application) : PeerDeviceController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(PeerUiState())
    override val state: StateFlow<PeerUiState> = _state.asStateFlow()

    private val peerAppName = "pair-peer"

    private var peerApp: FirebaseApp? = null
    private var peerAuth: FirebaseAuth? = null
    private var peerDb: FirebaseDatabase? = null

    private fun db(): FirebaseDatabase = requireNotNull(peerDb) { "Peer not started" }

    override fun start() {
        if (peerApp != null) {
            _state.update { it.copy(isReady = true) }
            return
        }
        scope.launchSafely("peer start") {
            val app = FirebaseProvider.createNamedInstance(application, peerAppName)
            peerApp = app
            peerAuth = FirebaseAuth.getInstance(app)
            peerDb = FirebaseDatabase.getInstance(app, requireDatabaseUrl())

            // Note: FCM registration is per *application*, and the SDK only exposes a
            // messaging object for the default FirebaseApp, so the peer shares this app's push
            // token. That is fine for a one-phone rig: when the peer changes the live text the
            // Cloud Function still pushes to this device, and the primary app's Live Update
            // updates exactly as it would for a real second phone.
            runCatching { FirebaseMessaging.getInstance().token.await() }

            _state.update {
                it.copy(
                    isReady = true,
                    message = "Second identity ready",
                )
            }
        }
    }

    private fun requireDatabaseUrl(): String {
        val url = FirebaseApp.getInstance().options.databaseUrl
        require(!url.isNullOrBlank()) { "No database URL in the Firebase configuration" }
        return url
    }

    override fun signIn(name: String) {
        scope.launchSafely("peer sign-in") {
            val auth = requireNotNull(peerAuth)
            val user = requireNotNull(auth.currentUser ?: auth.signInAnonymously().await().user)
            _state.update {
                it.copy(
                    isSignedIn = true,
                    uid = user.uid,
                    displayName = name.ifBlank { "Peer" },
                    lastError = null,
                )
            }
            // Register the peer's token so the function can address this device for the
            // peer's identity as well as the primary one.
            runCatching {
                val token = FirebaseMessaging.getInstance().token.await()
                db().reference.child("users/${user.uid}/fcmToken").setValue(token).await()
                db().reference.child("users/${user.uid}/displayName")
                    .setValue(name.ifBlank { "Peer" }).await()
            }.onFailure { Log.w(TAG, "Could not register peer token", it) }
        }
    }

    override fun join(roomIdInput: String, name: String) {
        scope.launchSafely("peer join") {
            val roomId = com.wood.pair.data.model.RoomId.normalize(roomIdInput)
            if (roomId == null) {
                _state.update { it.copy(lastError = "Not a valid room ID") }
                return@launchSafely
            }
            val uid = _state.value.uid
            if (uid == null) {
                _state.update { it.copy(lastError = "Sign the peer in first") }
                return@launchSafely
            }

            _state.update { it.copy(isBusy = true, lastError = null) }

            // The same claim the real app performs: a transaction flipping the public slot
            // hint, which the rules independently restrict to a single winner.
            val slotRef = db().reference.child("roomSlots/$roomId")
            val outcome = AtomicReference(CLAIM_FAILED)
            val transactionResult = slotRef.awaitTransaction(
                object : com.google.firebase.database.Transaction.Handler {
                    override fun doTransaction(
                        current: com.google.firebase.database.MutableData,
                    ): com.google.firebase.database.Transaction.Result {
                        if (current.isAbsent()) {
                            outcome.set(CLAIM_NO_ROOM)
                            return com.google.firebase.database.Transaction.abort()
                        }
                        if (current.getValue(Boolean::class.java) == true) {
                            outcome.set(CLAIM_FULL)
                            return com.google.firebase.database.Transaction.abort()
                        }
                        current.value = true
                        outcome.set(CLAIM_OK)
                        return com.google.firebase.database.Transaction.success(current)
                    }

                    override fun onComplete(
                        error: com.google.firebase.database.DatabaseError?,
                        committed: Boolean,
                        current: com.google.firebase.database.DataSnapshot?,
                    ) {
                        if (!committed) outcome.set(CLAIM_FAILED)
                    }
                },
            )
            if (!transactionResult && outcome.get() == CLAIM_OK) {
                outcome.set(CLAIM_FAILED)
            }

            when (outcome.get()) {
                CLAIM_NO_ROOM -> {
                    _state.update { it.copy(isBusy = false, lastError = "Room not found") }
                    return@launchSafely
                }

                CLAIM_FULL -> {
                    _state.update { it.copy(isBusy = false, lastError = "Room is full") }
                    return@launchSafely
                }

                CLAIM_FAILED -> {
                    _state.update { it.copy(isBusy = false, lastError = "Could not join") }
                    return@launchSafely
                }

                else -> Unit
            }

            val displayName = name.ifBlank { _state.value.displayName }
            db().reference.child("rooms/$roomId/member")
                .setValue(mapOf("uid" to uid, "name" to displayName)).await()

            _state.update {
                it.copy(isBusy = false, roomId = roomId, displayName = displayName, message = "Paired")
            }
        }
    }

    override fun sendLiveText(roomId: String, text: String) {
        scope.launchSafely("peer live text") {
            val uid = _state.value.uid ?: return@launchSafely
            // Deliberately debounced the same way the app does, so a burst of text produces
            // one write rather than one per keystroke.
            delay(DEBOUNCE_MILLIS)
            // Scoped to the peer's own uid, exactly as the real app does. The two members'
            // statuses are independent slots, so the peer's text lands in the peer's slot and
            // arrives at the app as the partner's status rather than overwriting the app's own.
            db().reference.child("rooms/$roomId/live/$uid").setValue(
                mapOf(
                    "text" to text.take(Room.MAX_TEXT_LENGTH),
                    "updatedAt" to com.google.firebase.database.ServerValue.TIMESTAMP,
                ),
            ).await()
        }
    }

    override fun setPostsOwnLiveUpdate(enabled: Boolean) {
        _state.update { it.copy(postsOwnLiveUpdate = enabled) }
        // Turning it off has to actually remove the notification, not just stop future ones, or
        // the last thing the peer sent would sit in the shade indefinitely.
        if (!enabled) {
            val roomId = _state.value.roomId ?: return
            PeerNotifier.cancel(application, roomId)
        }
    }

    override fun postLiveUpdate(roomId: String, text: String) {
        // The peer's notification and the app's are the same words. Posting both made every
        // incoming message look like two events. Opt in to this when the question is "does the
        // peer's own Live Update work", not "does the app receive one".
        if (!_state.value.postsOwnLiveUpdate) return
        scope.launchSafely("peer live update") {
            PeerNotifier.show(application, roomId, text)
        }
    }

    override fun leave() {
        scope.launchSafely("peer leave") {
            val roomId = _state.value.roomId
            val uid = _state.value.uid
            if (roomId != null && uid != null) {
                runCatching {
                    db().reference.child("rooms/$roomId/member").setValue(null).await()
                    db().reference.child("roomSlots/$roomId").setValue(false).await()
                    // Take the status with us. The app falls back to showing its own once the
                    // slot is empty, so a stale value here would be a message from someone who
                    // is no longer in the room.
                    db().reference.child("rooms/$roomId/live/$uid").setValue(null).await()
                }
                PeerNotifier.cancel(application, roomId)
            }
            _state.update { it.copy(roomId = null, message = "Left") }
        }
    }

    override fun reset() {
        scope.launchSafely("peer reset") {
            val uid = _state.value.uid
            if (uid != null) {
                runCatching { db().reference.child("users/$uid").setValue(null).await() }
            }
            runCatching { requireNotNull(peerAuth).signOut() }
            _state.update {
                PeerUiState(isReady = true, message = "Second identity reset")
            }
        }
    }

    /** Exposed so the peer can watch a room without a second screen. */
    suspend fun peek(roomId: String): String? =
        db().reference.child("rooms/$roomId/live/text").snapshotFlow().firstOrNull()
            ?.getValue(String::class.java)

    private companion object {
        const val TAG = "PeerDevice"
        const val DEBOUNCE_MILLIS = 300L
        const val CLAIM_OK = 0
        const val CLAIM_NO_ROOM = 1
        const val CLAIM_FULL = 2
        const val CLAIM_FAILED = 3
    }
}
