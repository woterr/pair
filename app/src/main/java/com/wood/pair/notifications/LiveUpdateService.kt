package com.wood.pair.notifications

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import com.wood.pair.PairApplication
import com.wood.pair.data.model.RoomId
import com.wood.pair.data.repository.RoomObservation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Owns the ongoing Live Update notification.
 *
 * Why a foreground service is required
 * -----------------------------------
 * On Android 16+ a notification only becomes a *promoted* Live Update — the status bar chip
 * and the always-on display chip — when it is owned by a foreground service. Posting the same
 * notification object from the background is accepted and appears in the shade, but the
 * system declines to promote it: `Notification.FLAG_PROMOTED_ONGOING` is simply never set,
 * with no error. This was verified on an Android 17 device, where the equivalent
 * background notification was created with `requestPromotedOngoing=true` and
 * `shortCriticalText` set correctly, and still not promoted.
 *
 * This service is therefore not decoration. It exists purely so the Live Update can become
 * the chip the product depends on, and it is stopped the moment that is no longer true:
 * on leave, when membership lapses, or when the user turns Live Updates off.
 *
 * ## Why it watches the room rather than only holding a notification
 * -----------------------------------------------------------------
 * A live status that only refreshes when you happen to open the app is not a live status.
 * The chip is the only surface Pair is *always* showing, and it is read at exactly the
 * moments the user cannot act on it — glancing at the screen, pulling the shade to check
 * whether they have been reached.
 *
 * Getting that right used to need a server. The Cloud Function in `functions/` is the
 * designed answer: it observes the database and pushes over FCM, so the chip changes with
 * the app closed. It cannot be deployed, though — Cloud Functions, Cloud Run, Eventarc,
 * Cloud Build and Artifact Registry all require the Blaze plan, and this project is on the
 * free Spark plan. See the README.
 *
 * So the wake-up is done here instead, and it turns out to need no server at all: this
 * service is *already running* for as long as there is a chip, because owning a foreground
 * service is what the chip requires. All that was missing was a listener. While the Live
 * Update is up, this service now watches the room and re-posts the notification whenever
 * the status it should be showing changes, so the chip tracks the partner's status with the
 * app closed and untouched.
 *
 * The rule for *what* to show is unchanged and is not re-implemented here: it is
 * [com.wood.pair.data.model.LiveTexts.forViewer], the same expression the app and the reply
 * receiver use, so the chip cannot disagree with the room screen. Re-deriving it in one
 * shared place is the point — the earlier arrangement had the view model resolve the text
 * and this service take it on trust, which is two answers to one question.
 *
 * Cost, stated plainly: this is a database connection held open for as long as there is a
 * status to show. It is not polling — it is one listener on one node, idle when nothing
 * changes. It stops the moment there is nothing to show, so a room with no status costs
 * nothing. It is ended by a force-stop from Android recents, which no app can survive, and
 * that is the honest limit of this approach.
 *
 * Scope, and how it is used:
 *  - [show] posts or updates the notification and starts the watch. Called while the room
 *    screen is live, and by [com.wood.pair.fcm.PairMessagingService] when a remote push
 *    does arrive, should the project ever move to a billing plan.
 *  - [cancel] withdraws the chip and stops the service.
 */
class LiveUpdateService : Service() {

    /**
     * Owns the room listener. Deliberately *not* a child of any view model: this has to
     * outlive the UI, which is the entire reason the chip works when the app is closed.
     * A `SupervisorJob` so one failed update cannot take the service down with it.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var watchJob: Job? = null

    /** The text currently on screen, so a redundant room event does not re-post. */
    private var shown: String? = null

    /** The room this service is holding a chip for, needed to withdraw the right one. */
    private var watchedRoomId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopLiveUpdate()
            return START_NOT_STICKY
        }

        val roomId = intent?.getStringExtra(EXTRA_ROOM_ID) ?: run {
            // A sticky restart delivers a null intent. Rather than drop the chip — which is
            // the one thing that must never go missing — the room is recovered from
            // preferences. This is safe after a leave: leaving clears `currentRoomId`
            // *before* it cancels the service, so a restart finds no room and stops.
            startFromStoredRoom()
            return START_STICKY
        }

        if (!RoomId.isValid(roomId)) {
            Log.w(TAG, "Started with an invalid room id; stopping")
            stopSelf()
            return START_NOT_STICKY
        }

        // A chip left over from another room would be a chip this service is no longer
        // watching, so it cannot be kept correct. Normally unreachable — a user is in one
        // room at a time — but a switch that did happen must not strand a status bar entry.
        watchedRoomId?.takeIf { it != roomId }?.let { LiveUpdateNotifier.cancel(this, it) }

        // Post what the caller already knows, so setting a status lights the chip up
        // immediately rather than after a round trip. The watch below then keeps it
        // correct, which matters because the intent's text is a snapshot and not a promise.
        watchedRoomId = roomId
        post(roomId, intent.getStringExtra(EXTRA_TEXT).orEmpty())
        watch(roomId)

        // Sticky, so the chip survives Android reclaiming the process under memory
        // pressure — routine for a background app, and the difference between a live
        // status and one that quietly stops updating.
        return START_STICKY
    }

    /**
     * Restores the chip after a sticky restart, with no room id in the intent.
     *
     * Reaches into the room rather than re-posting a stored string: the status that was
     * showing when the process was reclaimed is not necessarily the status to show now.
     */
    private fun startFromStoredRoom() {
        val app = applicationContext as? PairApplication
        if (app == null) {
            stopSelf()
            return
        }
        val graph = app.graph
        scope.launch {
            val roomId = graph.preferences.currentRoomIdOrNull()
            if (roomId == null) {
                // Nothing to restore. A restart with no room is the shape a leave leaves
                // behind, and it must not put a chip back.
                stopSelf()
                return@launch
            }

            // The foreground-service deadline is met before anything that can be slow.
            //
            // Android allows roughly five seconds between startForegroundService and
            // startForeground, and the room read below is a network call that can
            // legitimately outlast that — fifteen seconds is this project's own timeout.
            // Waiting for the read first would mean a crash with
            // ForegroundServiceDidNotStartInTimeException, so the chip goes up empty-state
            // and is replaced a moment later. A brief "nothing yet" on a rare process
            // reclaim is a far better outcome than losing the process to it.
            watchedRoomId = roomId
            post(roomId, "")

            // Wait for the session to be known before reading: on a cold start the
            // anonymous user may not be resolved yet, and reading the room without a uid
            // would fail for a reason that has nothing to do with the rules.
            graph.authRepository.awaitKnownSession()
            val uid = graph.authRepository.currentUidOrNull()
            if (uid == null) {
                withdraw()
                return@launch
            }
            val room = graph.roomRepository.readRoomOnce(roomId, uid)
            val text = room?.live?.forViewer(uid, room.partnerOf(uid)).orEmpty()
            if (text.isBlank()) {
                withdraw()
                return@launch
            }
            post(roomId, text)
            watch(roomId)
        }
    }

    /**
     * Keeps the chip in step with the room for as long as there is something to show.
     *
     * This is what makes a live status live: the partner changes their status, this
     * listener wakes, and the chip is re-posted — with the app closed, because the only
     * thing running is this service and the connection it already needs to hold its
     * foreground notification.
     *
     * Stops itself on a room it can no longer read, and on a status that has gone blank.
     * Withdrawing rather than showing an empty chip is the same rule the app follows: a
     * notification occupying a permanent place on someone's screen to say nothing is
     * worse than no notification.
     */
    private fun watch(roomId: String) {
        // Already watching this room, so leave the listener alone.
        //
        // The room screen calls `show` on every room event, and the watch reacts to those
        // same events. Without this guard each one would cancel and re-establish the
        // listener — a fresh database connection per event, with a window after each teardown
        // in which a change could be missed. The listener is the expensive part; the
        // notification it drives is not.
        if (watchedRoomId == roomId && watchJob?.isActive == true) return

        watchJob?.cancel()
        val app = applicationContext as? PairApplication ?: return
        val graph = app.graph

        watchJob = scope.launch {
            // Wait for the session to be known before reading the room. The service is
            // reachable before the anonymous user is resolved — a sticky restart, or a
            // notification action — and `currentUser` is null in that window. Without this the
            // chip would come up and then quietly never update, which is the worst possible
            // failure: it looks like it is working.
            graph.authRepository.awaitKnownSession()
            val uid = graph.authRepository.currentUidOrNull()
            if (uid == null) {
                Log.w(TAG, "No signed-in identity; not watching $roomId")
                return@launch
            }

            graph.roomRepository.observeRoom(roomId, uid).collect { observation ->
                val decision = decideLiveUpdate(
                    observation = observation,
                    uid = uid,
                    liveUpdateEnabled = graph.preferences.liveUpdateEnabled.first(),
                    currentlyShown = shown,
                )
                when (decision) {
                    LiveUpdateDecision.Unchanged -> Unit

                    is LiveUpdateDecision.Post -> post(roomId, decision.text)

                    LiveUpdateDecision.Withdraw -> {
                        Log.i(TAG, "Nothing left to show for $roomId; withdrawing")
                        withdraw()
                        return@collect
                    }
                }
            }
        }
    }

    /** Posts, or replaces, the chip for a room. */
    private fun post(roomId: String, text: String) {
        shown = text
        val notification = LiveUpdateNotifier.build(context = this, roomId = roomId, liveText = text)
        promote(notification, roomId)
    }

    /** Takes the chip down and stops the service, because there is nothing left to show. */
    private fun withdraw() {
        // The room is known here rather than passed in: every caller withdrawing is reacting
        // to a room event, and the one thing that must not be missed is cancelling the
        // notification itself — stopping the service without cancelling would leave an
        // orphaned chip on the status bar with nothing behind it.
        watchedRoomId?.let { LiveUpdateNotifier.cancel(this, it) }
        stopLiveUpdate()
    }

    private fun stopLiveUpdate() {
        watchJob?.cancel()
        watchJob = null
        shown = null
        watchedRoomId = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        watchJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Promotes the notification with [Service.startForeground], which is what makes the
     * system consider it for Live Update promotion.
     */
    private fun promote(notification: Notification, roomId: String) {
        val id = LiveUpdateNotifier.notificationId(roomId)
        runCatching {
            ServiceCompat.startForeground(
                this,
                id,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                },
            )
        }.onSuccess {
            LiveUpdateNotifier.recordPromotionResult(LiveUpdateNotifier.canBePromoted(this))
        }.onFailure { error ->
            // Starting a foreground service can be refused (background start limits, or a
            // revoked notification permission). That must not crash Pair, and the ordinary
            // notification has already been posted as a fallback by the caller.
            Log.w(TAG, "Could not start the Live Update foreground service", error)
            stopSelf()
        }
    }

    companion object {
        private const val TAG = "LiveUpdateService"

        const val ACTION_STOP = "com.wood.pair.action.STOP_LIVE_UPDATE"
        const val EXTRA_ROOM_ID = "roomId"
        const val EXTRA_TEXT = "text"

        /**
         * Creates or updates the Live Update for a room.
         *
         * Safe to call repeatedly: the same notification ID is reused, so each call replaces
         * the previous notification rather than stacking another one.
         */
        fun show(
            context: Context,
            roomId: String,
            liveText: String,
        ) {
            // Post it the ordinary way first. If the foreground service is refused, the user
            // still gets the Live Update in the shade, which is the graceful degradation.
            LiveUpdateNotifier.show(context, roomId, liveText)

            val manager = context.getSystemService(android.app.NotificationManager::class.java)
            if (manager?.areNotificationsEnabled() != true) return

            val intent = Intent(context, LiveUpdateService::class.java).apply {
                putExtra(EXTRA_ROOM_ID, roomId)
                putExtra(EXTRA_TEXT, liveText)
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }.onFailure { Log.w(TAG, "Could not request the Live Update service", it) }
        }

        /** Cancels the Live Update and stops the service. */
        fun cancel(context: Context, roomId: String) {
            LiveUpdateNotifier.cancel(context, roomId)
            runCatching {
                context.startService(
                    Intent(context, LiveUpdateService::class.java).setAction(ACTION_STOP),
                )
            }.onFailure { Log.d(TAG, "Live Update service was not running", it) }
        }
    }
}
