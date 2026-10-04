package com.wood.pair.notifications

import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.wood.pair.data.model.LiveTexts
import com.wood.pair.data.model.RoomId

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

        val scope = StatusSubmitter.transientScope()

        // A BroadcastReceiver is killed as soon as onReceive returns, so the work is held open
        // deliberately. Ten seconds is the platform's own limit for a broadcast.
        val pending = goAsync()

        // The write itself is [StatusSubmitter]'s, shared with the sheet the notification body
        // opens. Two entry points to one rule: kept here so they cannot drift apart.
        StatusSubmitter.submit(
            context = context,
            roomId = roomId,
            text = text,
            scope = scope,
            onDone = { pending.finish() },
        )
    }

    companion object {
        private const val TAG = "LiveUpdateReply"

        /** Must match the `RemoteInput` result key registered on the notification action. */
        const val KEY_STATUS_INPUT = "status_text"

        const val ACTION_REPLY = "com.wood.pair.action.REPLY_STATUS"
        const val EXTRA_ROOM_ID = "roomId"
    }
}
