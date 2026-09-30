package com.wood.pair.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.key
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.wood.pair.R
import com.wood.pair.ui.motion.rememberPressScale
import com.wood.pair.ui.theme.LocalMotionScheme
import com.wood.pair.ui.theme.decorTint
import com.wood.pair.ui.theme.navBarSurface
import com.wood.pair.ui.theme.pairShapes
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The three top-level destinations, in the order the comps show them.
 *
 * Each carries an outlined and a filled glyph. The outlined one is shown at rest and the filled
 * one when the destination is current, which is the standard Material pairing and the one the
 * design uses: an outline reads as available, a solid reads as *here*. Where a destination has
 * no outlined form — the gear, for instance, which is a single shape in Material's set — the
 * filled glyph is used in both states rather than inventing a second drawing.
 *
 * Home comes first because it is the frame the whole app resolves to — the other two are reached
 * from it, and it is the only destination that is never a dead end.
 */
enum class PairDestination(
    val outlinedIcon: ImageVector,
    val filledIcon: ImageVector,
    val labelRes: Int,
) {
    Home(
        outlinedIcon = Icons.Outlined.Home,
        filledIcon = Icons.Filled.Home,
        labelRes = R.string.nav_home,
    ),
    Pair(
        outlinedIcon = Icons.Outlined.FavoriteBorder,
        filledIcon = Icons.Filled.Favorite,
        labelRes = R.string.nav_pair,
    ),
    Location(
        outlinedIcon = Icons.Outlined.Place,
        filledIcon = Icons.Filled.Place,
        labelRes = R.string.nav_location,
    ),
}

/**
 * The one bottom-bar router, shared by every screen.
 *
 * ## Why the bar's behaviour is written once
 *
 * Each screen used to spell out its own `when` over the destinations, and four copies of the same
 * rule drifted. The copy that mattered was the Pair branch:
 *
 * ```kotlin
 * PairDestination.Pair -> state.currentRoomId?.let(onOpenRoom)
 * ```
 *
 * With no room, that expression is a no-op — so tapping Pair did *nothing*, with no movement and
 * no message. It reads exactly like a dropped tap, and it was the "navigation isn't working on the
 * first click" report. Silently ignoring a destination is never the right answer: either the item
 * goes somewhere, or it explains why it cannot.
 *
 * So Pair with no room now says so. That is the only honest outcome: the room tab is a real place
 * that does not exist yet, and the actionable response to finding that out is to learn where to
 * create or join one, which is what the message points at.
 */
@Composable
fun rememberDestinationRouter(
    currentRoomId: String?,
    onOpenHome: () -> Unit,
    onOpenRoom: (String) -> Unit,
    onOpenLocation: () -> Unit,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope = rememberCoroutineScope(),
): (PairDestination) -> Unit {
    val noRoom = stringResource(R.string.nav_no_room)
    return remember(currentRoomId, onOpenHome, onOpenRoom, onOpenLocation, noRoom) {
        val roomId = currentRoomId
        { destination: PairDestination ->
            when (destination) {
                PairDestination.Home -> onOpenHome()
                PairDestination.Location -> onOpenLocation()
                PairDestination.Pair -> {
                    if (roomId != null) {
                        onOpenRoom(roomId)
                    } else {
                        scope.launch { snackbarHostState.showSnackbar(noRoom) }
                    }
                }
            }
        }
    }
}

/**
 * The bottom bar: a stadium of destinations with the settings action as a filled rounded square
 * beside it.
 *
 * Built from [SmallFloatingActionButton] for the settings action so it keeps the platform's
 * touch target, ripple and minimum-tap behaviour, with the corner radius taken from the comp
 * instead of the default circle.
 */
@Composable
fun PairBottomBar(
    selected: PairDestination?,
    onSelect: (PairDestination) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Whether the Pair destination is shown at all.
     *
     * There is no room, so there is no room screen, and a navigation item that leads to a page
     * which does not exist is a dead control. It was previously always present and answered the
     * tap with a snackbar explaining the absence — which is *less* honest than not offering it,
     * because the bar then advertises a destination and withdraws it on contact. The router keeps
     * its snackbar as a backstop, but the item is now simply absent while the room is.
     *
     * The selection pill is positioned off the *visible* list, not off `PairDestination.ordinal`,
     * so removing an item moves the pill rather than stranding it between two.
     */
    showPair: Boolean = true,
) {
    val destinations = if (showPair) {
        PairDestination.entries
    } else {
        PairDestination.entries.filter { it != PairDestination.Pair }
    }
    val selectedIndex = destinations.indexOf(selected)
    val motion = LocalMotionScheme.current

    // Fast on both axes. The pill is a 52dp circle travelling a few dozen dp inside a bar that
    // does not itself move, and Material's table puts small components on the fast tokens. On
    // `default` spatial the pill trailed the page it was meant to be confirming, and the gap
    // between the press and the highlight arriving was the most noticeable lag in the app.
    val indicatorOffset by androidx.compose.animation.core.animateDpAsState(
        // Indexed against the *visible* list. The old code used `selected.ordinal`, which assumes
        // all three destinations are present; with the Pair item removed, the Location pill would
        // have been drawn at index 2 and sat past the end of the bar.
        targetValue = if (selectedIndex >= 0) (12.dp + (60 * selectedIndex).dp) else 12.dp,
        animationSpec = motion.fastSpatialSpec(),
        label = "navIndicatorOffset",
    )
    val indicatorAlpha by animateFloatAsState(
        targetValue = if (selectedIndex >= 0) 1f else 0f,
        animationSpec = motion.fastEffectsSpec(),
        label = "navIndicatorAlpha",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Box(
                modifier = Modifier
                    .background(
                        color = navBarSurface(),
                        shape = MaterialTheme.pairShapes.pill,
                    )
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (indicatorAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .offset(x = indicatorOffset)
                            .size(52.dp)
                            .graphicsLayer { alpha = indicatorAlpha }
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape,
                            ),
                    )
                }

                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    destinations.forEach { destination ->
                        NavItem(
                            destination = destination,
                            selected = destination == selected,
                            onClick = { onSelect(destination) },
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            val settingsInteraction = remember { MutableInteractionSource() }
            SmallFloatingActionButton(
                onClick = onSettings,
                shape = MaterialTheme.pairShapes.iconButton,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    focusedElevation = 0.dp,
                    hoveredElevation = 0.dp,
                ),
                modifier = Modifier
                    .size(56.dp)
                    .rememberPressScale(settingsInteraction),
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.home_settings),
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    destination: PairDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val motion = LocalMotionScheme.current

    val content by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        // Fast, and the same tick as the pill behind it. Both are driven by `fast`, so the glyph
        // has finished changing colour by the time the pill arrives. On `default` the colour was
        // still crossfading when the pill settled, and a selected item read as two half-finished
        // animations rather than one.
        animationSpec = motion.fastEffectsSpec(),
        label = "navContent",
    )

    Box(
        modifier = Modifier
            .size(52.dp)
            // Before the indication, and the reason the ripple is a circle.
            //
            // `ripple()` with no shape bounds itself to the *layout* rectangle, so on a 52dp
            // circular destination the press painted a 52x52dp rectangle around a round icon -
            // visible as a hard-edged square the instant a finger landed, and the most jarring
            // thing in the app's chrome. Clipping first constrains the same ripple to the
            // circle the item actually is.
            .clip(CircleShape)
            .selectable(
                selected = selected,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // The press scale lives on the *content*, not on the selectable. On the outer modifier it
        // would scale the ripple's own bounds along with the glyph, so the circle the ripple is
        // painted in would breathe with the press. Scaled here, the ripple stays put and only the
        // icon settles, which is what a press is supposed to look like.
        Box(
            modifier = Modifier
                .matchParentSize()
                .rememberPressScale(interactionSource),
            contentAlignment = Alignment.Center,
        ) {
            NavIcon(
                outlined = destination.outlinedIcon,
                filled = destination.filledIcon,
                selected = selected,
                contentDescription = stringResource(destination.labelRes),
                tint = content,
                modifier = Modifier.size(25.dp),
            )
        }
    }
}

/**
 * An icon that cross-fades from its outlined form to its filled form.
 *
 * The two glyphs are stacked and their opacities are driven by one animated value, rather than
 * one being swapped for the other. A swap pops — the shape changes on a single frame — whereas a
 * fade reads as the icon filling up, which is what actually happened.
 *
 * When a glyph has no outlined form the two are the same drawing, so the cross-fade is a no-op
 * and the icon simply stays as it is. That is the intended behaviour for those, not a gap.
 */
@Composable
private fun NavIcon(
    outlined: ImageVector,
    filled: ImageVector,
    selected: Boolean,
    contentDescription: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val motion = LocalMotionScheme.current

    // 0f is fully outlined, 1f is fully solid. Fast, because it is a 24dp glyph inside a 52dp
    // target and it has to land at the same moment as the pill behind it — both are driven by the
    // same `fast` tick, so the icon is solid by the time the pill arrives rather than a beat
    // behind it. The branch it used to carry was dead: `defaultEffectsSpec` already snaps under
    // reduced motion, so all it did was make the line look like it was doing something.
    val solid by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = motion.fastEffectsSpec(),
        label = "iconFill",
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Icon(
            imageVector = outlined,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1f - solid },
        )
        Icon(
            imageVector = filled,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = solid },
        )
    }
}

/**
 * The layout every screen sits in: optional background decoration, an optional top bar, the
 * content, and the bottom bar.
 *
 * [decor] is drawn first and is deliberately *not* inset-managed: its origin is the window's
 * top-left, not the content's. That is what lets a decorative shape be placed at a position
 * measured off a comp — the comps position their shapes against the screen edge, so anything
 * anchored to the inset content would drift by however much the status bar happens to be.
 *
 * @param bottomContentPadding how much room the caller has left for itself under the bar. It is
 *   a parameter rather than being computed here because the bar's height depends on the system
 *   inset, which only the caller knows how it wants to absorb.
 */
@Composable
fun PairScaffold(
    modifier: Modifier = Modifier,
    decor: (@Composable BoxScope.() -> Unit)? = null,
    topBar: (@Composable () -> Unit)? = null,
    bottomContentPadding: androidx.compose.ui.unit.Dp = 96.dp,
    selectedDestination: PairDestination? = null,
    onSelectDestination: (PairDestination) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    /**
     * Whether a room exists. Drives whether the Pair destination is in the bar at all.
     *
     * A parameter rather than something the bar reads for itself, because the bar has no view of
     * the room and every screen already knows the answer.
     */
    hasRoom: Boolean = true,
    snackbarHostState: androidx.compose.material3.SnackbarHostState? = null,
    content: @Composable (androidx.compose.ui.unit.Dp) -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (decor != null) {
            Box(modifier = Modifier.fillMaxSize()) { decor() }
        }

        // The app is edge to edge, so the shared chrome absorbs the status-bar inset itself.
        // Doing it here rather than in every screen is what keeps the top bar on the same line
        // across all of them.
        if (topBar != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
            ) { topBar() }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = if (topBar != null) TOP_BAR_HEIGHT else 0.dp)
                .statusBarsPadding(),
        ) {
            content(bottomContentPadding)
        }

        if (selectedDestination != null) {
            PairBottomBar(
                selected = selectedDestination,
                onSelect = onSelectDestination,
                onSettings = onOpenSettings,
                showPair = hasRoom,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        // Above the bar, because a snackbar that appears behind the navigation pill is a message
        // nobody reads. The bar is 104dp tall plus whatever the system inset adds, which is what
        // [bottomContentPadding] was measured against.
        if (snackbarHostState != null) {
            androidx.compose.material3.SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 104.dp)
                    .padding(horizontal = 20.dp),
            ) { data ->
                androidx.compose.material3.Snackbar(
                    snackbarData = data,
                    shape = MaterialTheme.pairShapes.cardSmall,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    actionColor = MaterialTheme.colorScheme.inversePrimary,
                )
            }
        }
    }
}

/** Height of the shared top bar, below the status-bar inset. */
private val TOP_BAR_HEIGHT = 56.dp

/** Height reserved for the bottom bar, above the navigation-bar inset. */
private val BAR_HEIGHT = 104.dp
