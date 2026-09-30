package com.wood.pair

import androidx.compose.runtime.Composable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wood.pair.ui.preview.SampleState
import com.wood.pair.ui.room.RoomContent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.material3.SnackbarHostState

/**
 * Renders the room screen in each of the states the product cares about.
 *
 * The infinite animations are frozen for the render: an indeterminate indicator never settles,
 * so a still of it would otherwise be a coin flip about where in its cycle it happened to be.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    application = TestApplication::class,
    qualifiers = "w411dp-h830dp-normal-long-notround-any-420dpi-keyshidden-nonav",
)
class RoomScreenRenderTest : ScreenRenderTest() {

    private fun render(name: String, state: com.wood.pair.ui.room.RoomUiState) {
        ScreenShots.render(compose, name) {
            RoomContent(
                state = state,
                roomId = SampleState.ROOM_ID,
                onCopyRoomId = {},
                onLiveTextChange = {},
                onEditingChanged = {},
                onClear = {},
                onSetStatus = {},
                onSelectDestination = {},
                onOpenSettings = {},
                snackbarHostState = SnackbarHostState(),
            )
        }
    }

    /**
     * Paused, with a partner: the state `text.png` depicts.
     *
     * The draft is deliberately non-empty, because the comp shows the field filled while the
     * indicator is up. That is the real paused state — the Live Update is withheld until the
     * field is cleared — and a render with an empty field would show a state the app cannot be in.
     */
    @Test
    fun roomPausedWithPartner() = render("room-paused", SampleState.roomPaused)

    /** Live: the same screen with text set, which is what withdraws the indicator. */
    @Test
    fun roomLive() = render("room-live", SampleState.roomLive)

    /**
     * Just after pressing "Set status": the confirmation chip is up and the button is disabled.
     *
     * The pair is the point. The chip says the status went out and the button is now inert because
     * there is nothing left to send — two statements about one write, and they have to agree.
     */
    @Test
    fun roomStatusSent() = render("room-sent", SampleState.roomSent)

    /** No partner yet: the line says so and drops the wave entirely. */
    @Test
    fun roomWaiting() = render("room-waiting", SampleState.roomWaiting)
}
