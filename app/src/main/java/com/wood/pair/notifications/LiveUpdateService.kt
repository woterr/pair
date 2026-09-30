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
 * on leave, when membership lapses, or when the user turns Live Updates off. It polls
 * nothing and does no work beyond holding one notification.
 *
 * Scope, and how it is used:
 *  - [show] posts or updates the notification. Called while the room screen is live.
 *  - [show] is also called from [com.wood.pair.fcm.PairMessagingService], which is what
 *    makes a remote update work when Pair is closed or killed. A high-priority FCM delivery
 *    grants the temporary allowlist needed to start a foreground service from the
 *    background.
 */
class LiveUpdateService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopLiveUpdate()
            return START_NOT_STICKY
        }

        val roomId = intent?.getStringExtra(EXTRA_ROOM_ID)
        if (roomId.isNullOrBlank()) {
            Log.w(TAG, "Started without a room id; stopping")
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = LiveUpdateNotifier.build(
            context = this,
            roomId = roomId,
            liveText = intent.getStringExtra(EXTRA_TEXT).orEmpty(),
        )
        promote(notification, roomId)

        // Not sticky: if the process dies, the Live Update should go with it rather than
        // reappearing with stale text. A fresh update restarts the service.
        return START_NOT_STICKY
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

    private fun stopLiveUpdate() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
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
