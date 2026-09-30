package com.wood.pair.debug

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.wood.pair.MainActivity
import com.wood.pair.R

/**
 * The peer's own Live Update.
 *
 * Uses a separate channel and a separate notification ID from the main app so that both
 * "devices" are visible at once on one phone. The peer's notification deliberately has no
 * deep link into the app: tapping it should do nothing, because the peer is not the app.
 */
internal object PeerNotifier {

    const val CHANNEL_ID: String = "pair_live_update_peer"

    private const val ID_OFFSET = 0x1000

    fun notificationId(roomId: String): Int = ID_OFFSET + (roomId.hashCode() and 0x0FFF)

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Pair (second device)",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
            },
        )
    }

    fun show(context: Context, roomId: String, liveText: String) {
        ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val full = liveText.ifBlank { "Nothing yet" }
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pair_blob_outline)
            .setContentTitle("Pair · second device")
            .setContentText(full)
            .setCategory(Notification.CATEGORY_STATUS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setLocalOnly(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            // No deep link: the peer is a separate identity, not this app's room screen.
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    notificationId(roomId),
                    Intent(context, MainActivity::class.java)
                        .setAction(Intent.ACTION_VIEW)
                        .setData(Uri.parse("pair://room/$roomId"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            builder.setShortCriticalText(com.wood.pair.notifications.LiveUpdateNotifier.compactText(full))
            builder.setRequestPromotedOngoing(true)
        }

        runCatching { manager.notify(notificationId(roomId), builder.build()) }
    }

    fun cancel(context: Context, roomId: String) {
        runCatching { NotificationManagerCompat.from(context).cancel(notificationId(roomId)) }
    }
}
