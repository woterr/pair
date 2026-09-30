package com.wood.pair

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wood.pair.ui.home.HomeContent
import com.wood.pair.ui.preview.SampleState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the home screen at the comps' own dimensions.
 *
 * Two states, because the nav bar differs between them: with a room there are three destinations,
 * without one the Pair destination is absent entirely — there is no room screen to go to, and a
 * bar that advertises it and then refuses is worse than a bar that does not offer it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    application = TestApplication::class,
    qualifiers = "w411dp-h830dp-normal-long-notround-any-420dpi-keyshidden-nonav",
)
class HomeScreenRenderTest : ScreenRenderTest() {

    @Test
    fun homeWithRoom() {
        ScreenShots.render(compose, "home") {
            HomeContent(
                state = SampleState.home,
                avatarResId = com.wood.pair.ui.components.Avatars.resIdOf(SampleState.home.avatarId),
                onCreateRoom = {},
                onShowJoin = {},
                onDismissJoin = {},
                onJoinInputChange = {},
                onJoin = {},
                onOpenRoom = {},
                onOpenSettings = {},
                onOpenLocation = {},
                onDismissError = {},
            )
        }
    }

    /**
     * No room, so: no "Continue to room" card, and a two-destination bar.
     *
     * Also the check that the selection pill is still under the *right* item after the third
     * destination is removed — the pill is positioned off the visible list, and this is the only
     * render that would catch it being positioned off the enum's ordinals instead.
     */
    @Test
    fun homeWithoutRoom() {
        ScreenShots.render(compose, "home-no-room") {
            HomeContent(
                state = SampleState.homeNoRoom,
                avatarResId = com.wood.pair.ui.components.Avatars.resIdOf(SampleState.homeNoRoom.avatarId),
                onCreateRoom = {},
                onShowJoin = {},
                onDismissJoin = {},
                onJoinInputChange = {},
                onJoin = {},
                onOpenRoom = {},
                onOpenSettings = {},
                onOpenLocation = {},
                onDismissError = {},
            )
        }
    }
}
