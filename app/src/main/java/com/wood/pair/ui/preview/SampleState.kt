package com.wood.pair.ui.preview

import com.wood.pair.data.model.ConnectionState
import com.wood.pair.data.model.LiveTexts
import com.wood.pair.data.model.LocationRule
import com.wood.pair.data.model.Room
import com.wood.pair.data.model.TimeWindow
import com.wood.pair.ui.home.HomeUiState
import com.wood.pair.ui.room.RoomUiState
import com.wood.pair.ui.rules.RuleEditorUiState
import com.wood.pair.ui.rules.RulesUiState
import com.wood.pair.ui.settings.SettingsUiState
import com.wood.pair.ui.theme.ThemeMode

/**
 * Sample state for rendering the screens.
 *
 * The comps all name their people "Ada" and show a six-character room code, so the samples use
 * those. Everything here is a plain value: rendering a screen must never require Firebase, a
 * view model or a signed-in account, or the screenshots stop being reproducible.
 *
 * Lives in the main source set rather than in tests so previews, screenshot tests and manual
 * inspection all render the *same* states — a screenshot that disagrees with the preview is a
 * screenshot nobody trusts.
 */
object SampleState {

    const val NAME = "Ada"
    const val ROOM_ID = "YVXM2M"
    const val PARTNER = "Snoopy"
    const val LIVE_TEXT = "Going to…"

    val home = HomeUiState(
        displayName = NAME,
        currentRoomId = ROOM_ID,
        avatarId = "12",
        connection = ConnectionState.Connected,
    )

    val roomPaused = RoomUiState(
        roomId = ROOM_ID,
        displayName = NAME,
        avatarId = "12",
        connection = ConnectionState.Connected,
        // A draft with no confirmed live text is the real paused state: the app withholds the
        // Live Update until the field is cleared, and `text.png` shows exactly that — the field
        // filled, the indicator up. An empty draft would be a state the app cannot reach.
        draft = LIVE_TEXT,
        myPublishedText = "",
        partnerText = "",
        isPartnerPresent = true,
        partnerName = PARTNER,
        isLoading = false,
    )

    val roomLive = roomPaused.copy(draft = LIVE_TEXT, myPublishedText = LIVE_TEXT, partnerText = LIVE_TEXT)

    /**
     * The moment after pressing "Set status".
     *
     * Worth its own sample because it is the state that proves the button and the chip agree: the
     * draft now equals what is published, so the button is disabled *and* the chip is up. If those
     * ever disagree — a live button next to a "sent" chip — the screen is claiming two different
     * things about the same write.
     */
    val roomSent = roomLive.copy(sentText = LIVE_TEXT)

    /** A room with nobody in it yet, so the editor is the only thing on screen. */
    val roomWaiting = roomPaused.copy(
        isPartnerPresent = false,
        partnerName = null,
    )

    /** Home with no room, which is the state that hides the Pair destination from the nav bar. */
    val homeNoRoom = home.copy(currentRoomId = null)

    val rules = RulesUiState(
        rules = listOf(
            LocationRule(
                id = "home",
                label = "Home",
                latitude = 12.7190,
                longitude = 77.2938,
                radiusMeters = 200f,
                timeWindow = null,
                wallpaperUri = "",
                isActive = true,
            ),
            LocationRule(
                id = "work",
                label = "Work",
                latitude = 12.9716,
                longitude = 77.5946,
                radiusMeters = 200f,
                timeWindow = TimeWindow(
                    startMinute = 9 * 60,
                    endMinute = 17 * 60,
                    daysOfWeek = setOf(2, 3, 4, 5, 6),
                ),
                wallpaperUri = "",
                isActive = true,
            ),
        ),
        isLoading = false,
        displayName = NAME,
        avatarId = "12",
        currentRoomId = ROOM_ID,
    )

    val ruleEditor = RuleEditorUiState(
        placeName = "Home",
        label = "Home",
        latitude = 12.7190,
        longitude = 77.2938,
        // The new-rule default, so the render shows what someone actually gets. 200f is still
        // the slider's floor, but no rule should be born pinned to it.
        radiusMeters = LocationRule.DEFAULT_RADIUS_METERS,
        hasTimeRestriction = true,
        startMinute = 9 * 60,
        endMinute = 21 * 60,
        daysOfWeek = setOf(2, 3, 4, 5, 6),
        displayName = NAME,
        avatarId = "12",
        currentRoomId = ROOM_ID,
    )

    val settings = SettingsUiState(
        displayName = NAME,
        currentRoomId = ROOM_ID,
        avatarId = "12",
        themeMode = ThemeMode.System,
        dynamicColor = true,
        amoledDark = true,
        liveUpdateEnabled = true,
        connection = ConnectionState.Connected,
    )
}

/** A room value for the samples, so nothing has to reach for a network to render. */
internal fun sampleRoom(): Room = Room(
    id = SampleState.ROOM_ID,
    ownerUid = "owner",
    memberUid = "member",
    ownerName = SampleState.NAME,
    memberName = SampleState.PARTNER,
    live = LiveTexts(byUid = mapOf("owner" to SampleState.LIVE_TEXT)),
    createdAt = 0L,
)
