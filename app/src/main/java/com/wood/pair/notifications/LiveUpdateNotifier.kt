package com.wood.pair.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationManagerCompat
import com.wood.pair.MainActivity
import com.wood.pair.R
import android.app.RemoteInput
import android.graphics.drawable.Icon
import com.wood.pair.data.model.Room

/**
 * The Pair Live Update.
 *
 * A real Android ongoing notification built on the platform's Live Update support:
 * `Notification.Builder.setShortCriticalText` supplies the status bar chip text and
 * `setRequestPromotedOngoing` requests promotion. There is no overlay, no custom view and no
 * accessibility trickery — where the platform will not promote, Pair says so rather than
 * faking a chip.
 *
 * The platform [Notification.Builder] is used directly (instead of `NotificationCompat`)
 * because the two Live Update setters only exist on the platform builder.
 *
 * Compact-text strategy
 * ---------------------
 * The platform documents roughly 7 characters for the status bar chip and ellipsizes
 * anything longer. This supplies at most that many characters and lets the system
 * ellipsize, rather than pretending a whole sentence fits. The untruncated text always
 * remains in the expanded notification.
 */
object LiveUpdateNotifier {

    private const val TAG = "LiveUpdate"

    /** Channel for the ongoing Live Update. */
    const val CHANNEL_ID: String = "pair_live_update"

    /**
     * Maximum characters handed to the compact chip, per the platform's documented guidance
     * for `setShortCriticalText`.
     */
    const val MAX_SHORT_TEXT_CHARS: Int = 7

    /** First Android version supporting promoted Live Updates in the status bar. */
    val liveUpdateSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA

    /**
     * Stable notification ID for a room, so an update *replaces* one notification instead of
     * stacking a new one per edit.
     */
    fun notificationId(roomId: String): Int {
        var hash = 7
        for (char in roomId) hash = hash * 31 + char.code
        // Confined to 0x3000..0x3FFF so it cannot clash with another notification Pair posts.
        return 0x3000 + (hash and 0x0FFF)
    }

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.settings_live_updates),
            // LOW importance: a Live Update is a persistent status surface, not an alert.
            // This is also what stops every keystroke producing a heads-up banner.
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.room_live_label)
            setShowBadge(false)
            enableVibration(false)
            enableLights(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    /** True when the system will let Pair post promoted ongoing notifications. */
    fun canBePromoted(context: Context): Boolean = try {
        context.getSystemService(NotificationManager::class.java)
            ?.canPostPromotedNotifications() == true
    } catch (e: Throwable) {
        // Absent before API 36; report unsupported rather than guessing.
        Log.d(TAG, "canPostPromotedNotifications unavailable: $e")
        false
    }

    /**
     * Explains, in logs, why a Live Update may not be appearing in the status bar or on the
     * always-on display.
     *
     * Promotion is an OS/user decision, not an app one: the app cannot grant it to itself.
     * When it is refused the notification is still posted and still ongoing — it simply is
     * not shown as a status bar chip — so this is worth recording rather than leaving the
     * user to wonder.
     */
    private fun logPromotionStatus(context: Context) {
        if (!liveUpdateSupported) {
            Log.i(TAG, "API ${Build.VERSION.SDK_INT} < 36: no promoted Live Updates available")
            return
        }
        val canPromote = canBePromoted(context)
        recordPromotionResult(canPromote)
    }

    /**
     * Records whether the system will actually show a chip.
     *
     * Promotion is an OS and user decision that the app cannot make for itself, so the honest
     * thing is to state it plainly in the log rather than leave the user wondering why the
     * chip is missing.
     */
    fun recordPromotionResult(canPromote: Boolean) {
        Log.i(
            TAG,
            if (canPromote) {
                "Promoted Live Updates are permitted; the status bar and AOD chip are available"
            } else {
                "Promoted Live Updates are NOT permitted for this app " +
                    "(POST_PROMOTED_NOTIFICATIONS). The Live Update is posted and ongoing, " +
                    "and appears in the shade, but the system will not show a status bar or " +
                    "AOD chip. This is an OS/user setting, not an app error."
            },
        )
    }

    /**
     * Creates or updates the Live Update for a room.
     *
     * @param liveText full current live text, shown expanded and on the chip
     */
    fun show(
        context: Context,
        roomId: String,
        liveText: String,
    ) {
        ensureChannels(context)
        logPromotionStatus(context)
        val manager = NotificationManagerCompat.from(context)

        // Notifications being switched off is a real, user-owned state rather than a fault.
        // The in-app Live state keeps working; only the status surface is absent.
        if (!manager.areNotificationsEnabled()) {
            Log.i(TAG, "Notifications disabled; Live Update not posted")
            return
        }

        runCatching { manager.notify(notificationId(roomId), build(context, roomId, liveText)) }
            .onFailure { Log.w(TAG, "Could not post Live Update for $roomId", it) }
    }

    /** Cancels the Live Update for one room. */
    fun cancel(context: Context, roomId: String) {
        runCatching { NotificationManagerCompat.from(context).cancel(notificationId(roomId)) }
            .onFailure { Log.w(TAG, "Could not cancel Live Update for $roomId", it) }
    }

    /**
     * Cancels every Pair Live Update except [keepRoomId], so a Live Update from a previously
     * active room cannot linger in the status bar.
     */
    fun cancelAllExcept(context: Context, keepRoomId: String?) {
        val keep = keepRoomId?.let(::notificationId)
        val manager = NotificationManagerCompat.from(context)
        manager.activeNotifications
            ?.filter { it.id != keep && it.notification.channelId == CHANNEL_ID }
            ?.forEach { runCatching { manager.cancel(it.id) } }
    }

    /**
     * Builds the Live Update notification.
     *
     * Exposed so [LiveUpdateService] can post the *same* notification instance under the same
     * ID. That matters: a foreground-service notification and an ordinary notification with
     * one ID would fight each other, and the chip would flicker between them.
     *
     * The composition is deliberately three parts and nothing else: the app's mark, the app's
     * name, and the message. No attribution line, no progress, no "room ABC123" footer — the
     * partner's status is the whole payload, and a Live Update has one job.
     */
    fun build(
        context: Context,
        roomId: String,
        liveText: String,
    ): Notification {
        val fullText = liveText.ifBlank { context.getString(R.string.room_live_text_empty_label) }

        val builder = Notification.Builder(context, CHANNEL_ID)
            // The status-bar chip draws the small icon, and the system tints it to match the
            // shade. The outline version of the mark is what stays legible when it is reduced
            // to a few dozen pixels and recoloured.
            //
            // No large icon. In the shade's Live Update template the large icon is drawn at the
            // *trailing* edge, opposite the small icon, so setting it put a second copy of the
            // brand mark on the right of every notification. The notification is meant to read as
            // mark, title, message - one mark, on the left, where the eye starts.
            .setSmallIcon(R.drawable.ic_pair_blob_outline)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(fullText)
            .setCategory(Notification.CATEGORY_STATUS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setLocalOnly(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            // The body tap is NOT a deep link any more.
            //
            // It used to open the room, which meant the fastest possible answer to "where are
            // they" - a glance at the chip, tap, type - cost an app launch, a Compose tree, a
            // network read and a wait. The chip is the *only* surface Pair shows permanently, so
            // it has to be actionable where it already is.
            //
            // With a [RemoteInput] action attached, the platform renders an inline text field in
            // the expanded Live Update, and a body tap expands the Live Update rather than firing
            // this intent. The intent is therefore the fallback for the cases where expansion
            // does not happen, and it opens the room - which is the right thing to do for
            // somebody who tapped the chip wanting to look at it rather than answer it.
            //
            // Being honest about the limit: there is no public API to *force* a Live Update to
            // expand, so on some launchers a body tap still lands here. On those, the inline
            // field is one tap away via the action, and the app opens. Forcing it to expand
            // would mean a translucent Activity that grabs focus, which is worse on every other
            // interaction.
            .setContentIntent(deepLink(context, roomId))
            .setDeleteIntent(deepLink(context, roomId))
            .addAction(replyAction(context, roomId))
            .addAction(openAction(context, roomId))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            applyLiveUpdateFields(builder, fullText)
        } else {
            // Before API 36 there is no status bar chip. The ongoing notification is still the
            // right surface, so it is posted as-is.
            Log.d(TAG, "Promoted Live Updates need API 36+; posting a plain ongoing notification")
        }

        return builder.build()
    }

    /** The two platform Live Update setters, both API 36+. */
    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun applyLiveUpdateFields(builder: Notification.Builder, fullText: String) {
        builder.setShortCriticalText(compactText(fullText))
        builder.setRequestPromotedOngoing(true)
    }

    /**
     * Builds the compact chip text.
     *
     * Collapses whitespace and supplies at most [MAX_SHORT_TEXT_CHARS] characters. The
     * platform ellipsizes anything longer, so no artificial ellipsis is added — that would
     * spend one of the very few available characters.
     *
     * `"I'm getting home soon"` therefore becomes `"I'm get"`, with the full sentence still
     * present in the expanded notification.
     */
    fun compactText(text: String): String {
        val collapsed = text.trim().replace(Regex("\\s+"), " ")
        if (collapsed.isEmpty()) return "—"
        if (collapsed.length <= MAX_SHORT_TEXT_CHARS) return collapsed
        return collapsed.take(MAX_SHORT_TEXT_CHARS).trimEnd()
    }

    private fun deepLink(context: Context, roomId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("pair://room/$roomId")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            notificationId(roomId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * The inline reply field: "change the status without opening the app".
     *
     * A [RemoteInput] is the platform's own mechanism for this and there is no Compose equivalent.
     * The system renders the field inside the expanded Live Update, and hands the typed text back
     * to [LiveUpdateReplyReceiver] as part of the broadcast intent.
     *
     * `allowFreeFormInput` is on and the max length is the same [Room.MAX_TEXT_LENGTH] the app
     * enforces everywhere else, so the field cannot accept a status that the database rules or
     * the app would then have to reject — the write fails identically on both paths rather than
     * only here.
     *
     * The two request codes are different *per room* and different from each other, because
     * `PendingIntent` equality ignores extras: two actions that shared a request code would share
     * one intent, and the second room to post would be handed the first room's id.
     */
    private fun replyAction(context: Context, roomId: String): Notification.Action {
        val remoteInput = RemoteInput.Builder(LiveUpdateReplyReceiver.KEY_STATUS_INPUT)
            .setLabel(context.getString(R.string.room_status_reply_label))
            .setAllowFreeFormInput(true)
            .build()

        val reply = Intent(context, LiveUpdateReplyReceiver::class.java).apply {
            action = LiveUpdateReplyReceiver.ACTION_REPLY
            putExtra(LiveUpdateReplyReceiver.EXTRA_ROOM_ID, roomId)
        }

        return Notification.Action.Builder(
            Icon.createWithResource(context, R.drawable.ic_pair_blob_outline),
            context.getString(R.string.room_status_reply_action),
            PendingIntent.getBroadcast(
                context,
                replyRequestCode(roomId),
                reply,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            ),
        )
            .addRemoteInput(remoteInput)
            // Without `semanticsAction` the accessibility services announce the action as a bare
            // label with no hint that it takes typed input.
            .setSemanticAction(Notification.Action.SEMANTIC_ACTION_REPLY)
            .setAllowGeneratedReplies(true)
            .build()
    }

    /** A second action, so the app is still one tap away rather than only via the body. */
    private fun openAction(context: Context, roomId: String): Notification.Action =
        Notification.Action.Builder(
            Icon.createWithResource(context, R.drawable.ic_pair_wordmark),
            context.getString(R.string.room_status_open_action),
            deepLink(context, roomId),
        ).build()

    /** Distinct per room, and distinct from the reply code so the two never collide. */
    private fun replyRequestCode(roomId: String): Int =
        notificationId(roomId) * 31 + 17
}
