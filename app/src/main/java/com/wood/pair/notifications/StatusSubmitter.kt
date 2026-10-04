package com.wood.pair.notifications

import android.content.Context
import android.util.Log
import com.wood.pair.PairApplication
import com.wood.pair.core.launchSafely
import com.wood.pair.data.model.LiveTexts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Writes a status typed outside the app, and puts this device's own Live Update back in order.
 *
 * ## Why this exists as its own thing
 *
 * There are now two ways to set a status without opening Pair — an inline notification field and
 * the overlay the notification body opens — and both must end in *exactly* the same write. When
 * they were separate they would drift, and the drift would be invisible until the chip disagreed
 * with the room screen.
 *
 * So the rule lives here once:
 *
 *  - Write to `live/{myUid}`, the same key the app's own "Set status" uses, so the database rules
 *    see the same shape and the Cloud Function would see the same trigger if it were deployed.
 *  - Re-derive *this* device's chip from the room rather than echoing what was just typed. With a
 *    partner present the chip shows the partner's status, and the user's own words do not belong
 *    there.
 *
 * Both entry points refuse a room this device is no longer in. Writing anyway would put a status
 * under a uid that is not a member, which the rules reject and which is not the user's to do.
 */
object StatusSubmitter {

    private const val TAG = "StatusSubmitter"

    /** What happened, so a caller with a screen can tell "sent" from "refused". */
    sealed interface Outcome {
        /** Written, and the chip re-derived. */
        data object Sent : Outcome

        /** No signed-in identity yet, so nothing could be written. */
        data object NoIdentity : Outcome

        /** This device is not in that room any more. */
        data object NotInRoom : Outcome

        /** The write itself failed. */
        data class Failed(val message: String) : Outcome
    }

    /**
     * Publishes [text] as this device's status in [roomId], then re-derives the chip.
     *
     * An empty [text] is a real instruction — the same as the app's Clear — and is honoured. That
     * is only safe because this is reached by an explicit submission rather than a notification
     * that was merely dismissed; see [LiveUpdateReplyReceiver] for the distinction there.
     *
     * Runs on [scope], which the caller owns, because a BroadcastReceiver and an Activity have
     * very different lifetimes. [onDone] is called back with the result.
     */
    fun submit(
        context: Context,
        roomId: String,
        text: String,
        scope: CoroutineScope,
        onDone: (Outcome) -> Unit = {},
    ) {
        val app = context.applicationContext as? PairApplication
        if (app == null) {
            Log.w(TAG, "Not running under PairApplication; ignoring the submission")
            onDone(Outcome.NoIdentity)
            return
        }
        val graph = app.graph

        scope.launchSafely("status submission") {
            val uid = graph.authRepository.currentUidOrNull()
            if (uid == null) {
                // The anonymous sign-in is in flight or has been lost. The chip is left alone
                // rather than posting something the room does not agree with.
                Log.w(TAG, "No signed-in identity; ignoring the submission")
                onDone(Outcome.NoIdentity)
                return@launchSafely
            }

            if (graph.preferences.currentRoomIdOrNull() != roomId) {
                Log.w(TAG, "Not currently in $roomId; ignoring the submission")
                onDone(Outcome.NotInRoom)
                return@launchSafely
            }

            val trimmed = text.trim()
            runCatching { graph.roomRepository.setLiveText(uid, roomId, trimmed) }
                .onSuccess {
                    // Re-derive this device's own chip from the room. The write above only
                    // changed *our* status; what belongs in our Live Update is the partner's,
                    // and that is unchanged unless there is no partner and the rule is loopback.
                    val room = graph.roomRepository.readRoomOnce(roomId, uid)
                    val partnerUid = room?.partnerOf(uid)
                    val shown = room?.live
                        ?.forViewer(viewerUid = uid, partnerUid = partnerUid)
                        .orEmpty()
                    LiveUpdateService.show(context, roomId, shown)

                    Log.i(TAG, "Status set in $roomId from outside the app")
                    onDone(Outcome.Sent)
                }
                .onFailure { error ->
                    Log.w(TAG, "Status write failed for $roomId", error)
                    onDone(Outcome.Failed(error.message ?: "Could not set the status"))
                }
        }
    }

    /**
     * A scope for a caller with no lifecycle of its own, such as a BroadcastReceiver, which is
     * killed the moment its entry point returns.
     */
    fun transientScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Kept so the rule's home is referenced from the Live Update's own documentation. */
    internal fun describeRule() = "live/{uid}, re-derived via " + LiveTexts::class.simpleName
}
