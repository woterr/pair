package com.wood.pair.notifications

import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.wood.pair.PairApplication
import com.wood.pair.core.launchSafely
import com.wood.pair.data.model.LiveTexts
import com.wood.pair.data.model.RoomId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles a status typed straight into the Live Update.
 *
 * ## Why this exists
 *
 * The Live Update is the only surface Pair is *always* showing, and until now it was read-only:
 * tapping it opened the app, and the only way to change a status was to find the app, open the
 * room, and type. That is three steps to answer the one question the app exists for. A
 * [RemoteInput] action on the notification turns the chip into an input field, so the shortest
 * path from "I am somewhere now" to "they know" is: pull the shade, type, send.
 *
 * ## Why it writes the database rather than just reposting
 *
 * The status bar chip is a view of the room, not a copy of it. If this receiver only re-posted
 * the notification, the chip would show text that no other device could see — the chip would
 * disagree with the partner's phone and with the app the moment either was opened. So the write
 * goes to `live/{myUid}` exactly as the app's own "Set status" does, the Cloud Function pushes it
 * to the partner, and this device's chip is then re-derived from the room.
 *
 * ## Why the chip is re-derived rather than reused
 *
 * After the write, the correct thing for *this* device's Live Update is unchanged: with a partner
 * it is the partner's status, which this write did not touch, and with no partner it is the
 * writer's own. Reading the room back and applying the same rule as everywhere else
 * ([LiveTexts.forViewer]) is what keeps that true instead of accidentally echoing the writer's
 * words into their own chip while a partner is present.
 */
class LiveUpdateReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REPLY) return

        val roomId = intent.getStringExtra(EXTRA_ROOM_ID)
        if (roomId == null || !RoomId.isValid(roomId)) {
            Log.w(TAG, "Reply with an invalid room id: $roomId")
            return
        }

        // `RemoteInput.getResultsFromIntent` returns null when the action was tapped without the
        // text field ever having been filled in - which is what happens if the user dismisses the
        // notification with the input open. That is a cancel, not an empty status, and treating
        // it as one would silently clear somebody's status by them swiping a notification away.
        val text = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(KEY_STATUS_INPUT)
            ?.toString()
            ?.trim()
            ?: return

        if (text.isEmpty()) {
            // An explicitly empty reply is a real instruction: "I am not saying anything". That is
            // the same as the app's Clear, so it is honoured - but only when the user actually
            // submitted an empty field, which is the case that reaches here.
            Log.i(TAG, "Empty reply; withdrawing the status for $roomId")
        }

        val app = context.applicationContext as? PairApplication ?: return
        val graph = app.graph
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        // A BroadcastReceiver is killed as soon as onReceive returns, so the work is held open
        // deliberately. Ten seconds is the platform's own limit for a broadcast.
        val pending = goAsync()

        scope.launchSafely("live update reply") {
            try {
                val uid = graph.authRepository.currentUidOrNull()
                if (uid == null) {
                    // No session. The anonymous sign-in is in flight or has been lost, and
                    // writing under a null uid is not possible. The chip is left alone rather than
                    // posting something the room does not agree with.
                    Log.w(TAG, "No signed-in identity; ignoring the reply")
                    return@launchSafely
                }

                if (graph.preferences.currentRoomIdOrNull() != roomId) {
                    // The reply names a room this device is no longer in - it left, or was
                    // removed. Writing anyway would create a status under a uid that is not a
                    // member, which the database rules reject and which is not this user's to do.
                    Log.w(TAG, "Not currently in $roomId; ignoring the reply")
                    return@launchSafely
                }

                graph.roomRepository.setLiveText(uid, roomId, text)

                // Re-derive this device's own chip from the room. The write above only changed
                // *our* status; what belongs in our Live Update is the partner's, and that is
                // unchanged unless there is no partner and the rule is loopback.
                val room = graph.roomRepository.readRoomOnce(roomId, uid)
                val partnerUid = room?.partnerOf(uid)
                val shown = room?.live?.forViewer(viewerUid = uid, partnerUid = partnerUid).orEmpty()
                LiveUpdateService.show(context, roomId, shown)

                Log.i(TAG, "Status sent to $roomId from the notification")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "LiveUpdateReply"

        /** Must match the `RemoteInput` result key registered on the notification action. */
        const val KEY_STATUS_INPUT = "status_text"

        const val ACTION_REPLY = "com.wood.pair.action.REPLY_STATUS"
        const val EXTRA_ROOM_ID = "roomId"
    }
}
