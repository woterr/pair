package com.wood.pair.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.action.actionParametersOf
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.wood.pair.R
import com.wood.pair.data.PairGraph
import com.wood.pair.notifications.QuickStatusActivity
import com.wood.pair.ui.theme.Mono
import com.wood.pair.ui.theme.ThemeMode
import com.wood.pair.ui.theme.supportsDynamicColor
import kotlinx.coroutines.flow.first

/**
 * The home screen widget: the partner's status, and a way to answer it.
 *
 * ## Why a widget at all
 *
 * The Live Update guidance is explicit that ambient information — where a person is, what they are
 * up to — does not belong in a promoted notification, and that the sanctioned place for it is "an
 * app widget or a custom Quick Settings tile". Pair's status is exactly that kind of information:
 * ongoing, unowned, and about someone other than the reader.
 *
 * So this is not a second copy of the chip. It is the surface the platform asks this data to live
 * on: available without unlocking, costing nothing when dismissed, and never occupying the status
 * bar.
 *
 * ## The two things it does
 *
 * Partner's status, large, with their name above it — the answer to "where are they", which is the
 * question this app exists to answer. And a tap anywhere opens the same status sheet the Live
 * Update opens, so answering costs one tap and never an app launch.
 *
 * Widgets cannot host a text field — there is no input surface in a widget — so typing has to go
 * somewhere else. Reusing [QuickStatusActivity] means the write is the same one the notification
 * and the app perform, through `StatusSubmitter`, so there is still only one rule about what a
 * status is.
 *
 * ## Colour
 *
 * Material You, resolved per render from the same functions the app uses, falling back to the
 * monochrome scheme on a device without it. A widget that hard-codes its palette is the one thing
 * on a home screen that always looks like it belongs to someone else's phone.
 */
class PairWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Everything is read here, in the render's own coroutine, and passed in as plain values.
        // `provideContent` is a composition, not a suspend context, so a suspending read inside it
        // does not compile — and it would be the wrong place regardless, since each of these would
        // then re-run per recomposition.
        val graph = PairGraph(context)
        val appearance = graph.preferences.appearance.first()
        val roomId = graph.preferences.currentRoomIdOrNull()
        val room = roomId?.let { id -> readRoom(context, graph, roomId) }

        val dark = appearance.themeMode.resolveDark(context)
        val colors = if (appearance.dynamicColor && supportsDynamicColor) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            if (dark) Mono.Dark else Mono.Light
        }

        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(colors.surfaceContainerHigh)
                        .cornerRadius(28.dp)
                        .padding(18.dp)
                        .clickable(
                            actionStartActivity<QuickStatusActivity>(
                                // A user with no room has nothing to set a status about, and the
                                // sheet refuses a null room id anyway — so there is no action at
                                // all rather than one that opens and closes on its own.
                                if (roomId != null) {
                                    actionParametersOf(RoomIdKey to roomId)
                                } else {
                                    actionParametersOf()
                                },
                            ),
                        ),
                    verticalAlignment = Alignment.Vertical.Top,
                ) {
                    Text(
                        text = room?.partnerLabel ?: context.getString(R.string.widget_no_partner),
                        style = TextStyle(
                            color = ColorProvider(colors.onSurfaceVariant),
                            fontSize = 13.sp,
                        ),
                        maxLines = 1,
                    )

                    Spacer(GlanceModifier.height(6.dp))

                    val status = room?.status?.takeIf { it.isNotBlank() }
                    Text(
                        text = status ?: context.getString(R.string.widget_nothing_yet),
                        style = TextStyle(
                            color = ColorProvider(
                                // A partner with nothing to say is a state, not an absence of
                                // one: the muted colour says "nothing yet" rather than making the
                                // widget look broken or empty.
                                if (status == null) colors.onSurfaceVariant else colors.onSurface,
                            ),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 3,
                    )

                    if (status != null) {
                        Spacer(GlanceModifier.height(12.dp))
                        Text(
                            text = context.getString(R.string.widget_set_status_hint),
                            style = TextStyle(
                                color = ColorProvider(colors.primary),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }

    /** What the widget needs to know, read once per render. */
    private data class WidgetRoom(val partnerLabel: String, val status: String)

    /**
     * Reads the room once rather than subscribing.
     *
     * A widget render is a snapshot, not a live view: there is nowhere for a listener to push to,
     * and holding one open across a render is how a widget drains a battery. Pair repaints
     * instead — see [PairWidgetReceiver.refresh] — so the widget follows the room without
     * listening to it.
     */
    private suspend fun readRoom(
        context: Context,
        graph: PairGraph,
        roomId: String,
    ): WidgetRoom? {
        val uid = graph.authRepository.currentUidOrNull() ?: return null
        val room = graph.roomRepository.readRoomOnce(roomId, uid) ?: return null
        val partnerUid = room.partnerOf(uid)
        return WidgetRoom(
            partnerLabel = room.partnerNameOf(uid)?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.widget_partner),
            // The partner's status, or — with nobody else in the room — the viewer's own. The same
            // rule the Live Update uses, so the two surfaces cannot tell different stories.
            status = room.live.forViewer(viewerUid = uid, partnerUid = partnerUid),
        )
    }
}

/**
 * Receives the widget's own lifecycle events, and is the name the manifest points at.
 *
 * The launcher instantiates this; [GlanceAppWidgetReceiver] wires the rest.
 */
class PairWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PairWidget()
}

/** Repaints every placed widget. Called whenever the Live Update is posted, so they never lag. */
suspend fun PairWidgetReceiver.refresh(context: Context) {
    runCatching { PairWidget().updateAll(context) }
}

/**
 * The room the sheet should open for.
 *
 * Named to match [QuickStatusActivity.EXTRA_ROOM_ID] because Glance delivers action parameters as
 * intent extras under the key's own name, and the activity reads one constant. Two names for one
 * value would drift.
 */
private val RoomIdKey = ActionParameters.Key<String>(QuickStatusActivity.EXTRA_ROOM_ID)

/**
 * Repaints every placed widget.
 *
 * Called from [com.wood.pair.notifications.LiveUpdateService] whenever the Live Update is posted,
 * which is already the moment the status changed. A widget has no listener of its own — a render
 * is a snapshot — so this is what keeps it honest.
 *
 * Failures are swallowed on purpose: a widget that cannot be repainted is a stale number on a home
 * screen, and it must not be the thing that takes down the notification that caused the repaint.
 */
suspend fun refreshWidgets(context: Context) {
    runCatching { PairWidget().updateAll(context.applicationContext) }
}

/**
 * Whether [this] theme mode means a dark surface, *right now*.
 *
 * The app asks Compose this through `isSystemInDarkTheme()`, which a widget receiver cannot reach:
 * a render is not a composition, so the answer has to come from the configuration directly.
 * `System` therefore means "whatever the device is set to right now", which is the one case a
 * fixed choice would get wrong — a widget is on the home screen, so it is very often rendered
 * while the app has never been opened to notice the change.
 */
private fun ThemeMode.resolveDark(context: Context): Boolean = when (this) {
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
    ThemeMode.System ->
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
}
