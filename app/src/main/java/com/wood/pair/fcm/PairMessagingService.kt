package com.wood.pair.fcm

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.wood.pair.PairApplication
import com.wood.pair.data.model.RoomId
import com.wood.pair.notifications.LiveUpdateService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives Pair's data messages and turns them into Live Updates.
 *
 * This is the mechanism that makes CASE 4 work: the receiving device updates its Live Update
 * even though Pair's Compose screen was never resumed. A Realtime Database listener cannot do
 * this — once the process is killed the listener is gone — so FCM is what wakes the device.
 *
 * The service deliberately does **not** open the app. It only posts or updates the ongoing
 * notification; the user decides whether to come back by tapping it.
 */
class PairMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "FCM token rotated")
        val app = application as? PairApplication ?: return
        // Rotation must be persisted, or pushes would keep going to a dead registration.
        scope.launch { app.graph.fcmTokenRepository.onNewToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data
        val roomId = data[PairFcmContract.KEY_ROOM_ID]
        val liveText = data[PairFcmContract.KEY_LIVE_TEXT]
        val senderUid = data[PairFcmContract.KEY_SENDER_UID]

        if (!PairFcmContract.validate(roomId, liveText, senderUid)) {
            // Reject rather than post: a malformed or spoofed payload must not be able to
            // drive a Live Update.
            Log.w(TAG, "Discarding malformed Pair message: $data")
            return
        }

        val room = requireNotNull(roomId) { "validated above" }
        val text = requireNotNull(liveText)

        // The Cloud Function already resolves the recipient and never echoes to the sender,
        // so a message for our own UID is not expected. Guard anyway.
        val selfUid = (application as? PairApplication)?.graph?.authRepository?.currentUidOrNull()
        if (selfUid != null && senderUid == selfUid) {
            Log.d(TAG, "Ignoring echo of our own live text for $room")
            return
        }

        // Routed through the Live Update service rather than posted directly. That is what
        // lets a remote update become a promoted status bar / AOD chip even though Pair's
        // Compose screen was never resumed, and a high-priority FCM delivery is one of the
        // few cases where the system permits a background foreground-service start.
        LiveUpdateService.show(
            context = this,
            roomId = room,
            liveText = text,
        )
        Log.i(TAG, "Live Update updated for ${room.take(RoomId.LENGTH)}")
    }

    private companion object {
        const val TAG = "PairMessaging"
    }
}
