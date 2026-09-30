package com.wood.pair.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wood.pair.data.PairGraph
import com.wood.pair.data.model.RoomId
import com.wood.pair.ui.home.HomeScreen
import com.wood.pair.ui.onboarding.OnboardingScreen
import com.wood.pair.ui.room.RoomScreen
import com.wood.pair.ui.rules.LocationRulesScreen
import com.wood.pair.ui.rules.RuleEditorScreen
import com.wood.pair.ui.settings.SettingsScreen
import com.wood.pair.ui.theme.pairMotion
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.flow.mapNotNull
import com.wood.pair.ui.components.LocalNavPillPosition
import com.wood.pair.ui.components.PairDestination
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D

/** Destinations in the app. */
object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val LOCATION_RULES = "location_rules"

    const val ROOM_PATTERN = "room/{roomId}"
    fun room(roomId: String) = "room/$roomId"

    /** A null rule id means "create a new rule". */
    const val RULE_EDITOR_PATTERN = "rule_editor?ruleId={ruleId}"
    const val ARG_RULE_ID = "ruleId"
    fun ruleEditor(ruleId: String? = null) =
        if (ruleId == null) "rule_editor" else "rule_editor?ruleId=$ruleId"
}

/**
 * The app's navigation graph.
 *
 * Start destination is decided from locally persisted state rather than from the network, so
 * launching always lands somewhere definite. The three "must finish first" screens —
 * onboarding, leaving a room, and a room that is gone — all resolve through the same
 * [startDestination] observation, which is what keeps a stale room pointer from wedging
 * startup.
 */
@Composable
fun PairApp(
    graph: PairGraph,
    deepLinkRoomId: String?,
) {
    val navController = rememberNavController()
    val localState by graph.preferences.state.collectAsStateWithLifecycle(initialValue = null)

    val hasCompletedOnboarding = localState?.hasCompletedOnboarding == true

    // A room, if there is one. Read from the same preference the room screens resolve through, so
    // this cannot disagree with what they would find.
    val startRoomId = localState?.currentRoomId?.takeIf { RoomId.isValid(it) }

    /**
     * Whether the cached preferences have actually been read yet.
     *
     * `null` is not "no room" and is not "not onboarded" - it is *not known yet*. DataStore is
     * asynchronous, so for the first frame or two after a cold start the app genuinely does not
     * know who the user is, and every one of those states has a different screen to show.
     *
     * Nothing below may be built until this is true, and that gate is the fix for the bug this
     * replaces. `NavHost` reads its `startDestination` **once**, when the `NavController` is
     * created, and never again. Recomputing the value when preferences arrived therefore did
     * nothing at all: the first frame decided the whole session.
     *
     * And the first frame decided it wrongly twice over, because `hasCompletedOnboarding` was
     * `localState?.hasCompletedOnboarding == true`, which is `false` while loading. So a returning
     * user with a live room got, in sequence:
     *
     *   1. Onboarding - a returning user was shown the sign-up screen,
     *   2. Home       - "You are not in a room", contradicting the room they were in,
     *   3. the room   - only if the deep-link effect happened to rescue it, which it does not on a
     *                   cold start with no deep link.
     *
     * Waiting for the read costs one or two frames of nothing, painted with the app's own
     * background, and removes all three. It is the difference between the cached state being the
     * *first* thing shown rather than a correction applied to a wrong first guess.
     */
    if (localState == null) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize(),
        ) {}
        return
    }

    /**
     * Where the app opens.
     *
     * With a room, the room. The room is the product: someone opening Pair wants to see where the
     * other person is, and that is a tap away on Home. Home is the right *frame* - it holds the
     * room card, and the two Create/Join actions - but it is the wrong first screen for someone
     * who already has a room, because it puts the thing they came for one layer down and makes
     * them press a card to reach the single piece of live information the app exists to show.
     *
     * Without a room, Home, because there is nothing else to show and the Create/Join pair there
     * is the only way forward.
     *
     * Not wrapped in `remember`. Now that it is evaluated only once - behind the gate above - a
     * remembered value could only ever be stale: leaving a room and cold-starting again would
     * reuse the destination computed while the room still existed.
     */
    val startDestination = when {
        !hasCompletedOnboarding -> Routes.ONBOARDING
        startRoomId != null -> Routes.room(startRoomId)
        else -> Routes.HOME
    }

    // A Live Update tap arrives as a deep link. Navigating once the graph exists avoids racing
    // the initial composition.
    LaunchedEffect(deepLinkRoomId, hasCompletedOnboarding) {
        val roomId = deepLinkRoomId?.takeIf(RoomId::isValid)
        if (roomId != null && hasCompletedOnboarding) {
            navController.navigate(Routes.room(roomId))
        }
    }

    // One background for the whole app, taken from the resolved scheme.
    //
    // This is not decoration: the window background is transparent, so without this a screen
    // that does not paint its own background would show the previous screen (or nothing) and
    // the AMOLED true-black setting would silently not apply. Every screen therefore inherits
    // a correct, single background.
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize(),
    ) {
        // Material 3's shared-axis X transition: the outgoing screen slides a little way out and
        // fades while the incoming one arrives. The travel is small on purpose — Pair's screens
        // are siblings, not a stack being pushed, and a long slide would imply a hierarchy the
        // navigation does not have.
        //
        // Every spec comes from the app's own [com.wood.pair.ui.theme.MotionScheme], so reduced
        // motion collapses all of this to instant cuts in one place rather than at each screen.
        val motion = MaterialTheme.pairMotion

        // The bottom bar's selection pill, owned here so its position survives navigation.
        //
        // The bar itself is rebuilt by every destination, so a pill that remembers where it was
        // has to live above the NavHost. See [LocalNavPillPosition] for why the alternatives -
        // an `animateDpAsState` in the bar, or a "previous destination" threaded down - both
        // produce a pill that appears rather than moves.
        val pillPosition = remember { Animatable(0f) }

        CompositionLocalProvider(LocalNavPillPosition provides pillPosition) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            // Content moves, chrome does not.
            //
            // The transitions apply to the *body* of each destination, not to the destination as
            // a whole. Material's shared-axis pattern is a movement of the thing being changed:
            // when the user taps "Location" the page slides, while the bar they tapped stays put
            // and simply marks a different item. Sliding the bar along with the page makes the
            // control appear to slide out from under the finger that pressed it, and the
            // selection they are about to see arrives somewhere they did not press.
            //
            // The bar is drawn by `PairScaffold`, which each screen hosts, so the transitions
            // below are declared on the body slot that the scaffold keeps fixed.
            //
            // Speed: `slow` spatial. Material's own table puts full-screen animations on the slow
            // token and reserves `default` for things that partly cover the screen (sheets, an
            // expanded rail). A destination swap moves the entire screen, so it is the slow one.
            //
            // The accompanying fade is a *full-screen content* change, which the same table puts
            // on slow effects — so the exit fade used to be `fastEffectsSpec` while the slide
            // next to it was `default`, which put a 150ms opacity ramp under a 500ms movement.
            // Matching the pair is what makes the two read as one animation.
            enterTransition = {
                slideInHorizontally(
                    animationSpec = motion.slowSpatialSpec(),
                    initialOffsetX = { full -> full / 8 },
                ) + fadeIn(animationSpec = motion.slowEffectsSpec())
            },
            exitTransition = {
                // A fade, not a slide, and in the same direction as the incoming page. The
                // outgoing page moving *backwards* while the new one comes forwards is the
                // standard depth cue, and it keeps the two pages from crossing over each other
                // in the middle of the transition, which a slide-out-then-slide-in does.
                fadeOut(animationSpec = motion.defaultEffectsSpec())
            },
            popEnterTransition = {
                fadeIn(animationSpec = motion.defaultEffectsSpec())
            },
            popExitTransition = {
                slideOutHorizontally(
                    animationSpec = motion.slowSpatialSpec(),
                    targetOffsetX = { full -> full / 8 },
                ) + fadeOut(animationSpec = motion.defaultEffectsSpec())
            },
        ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                graph = graph,
                onContinue = {
                    navController.navigate(Routes.HOME) {
                        // Onboarding should not be reachable with the back gesture.
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                graph = graph,
                onOpenRoom = { roomId -> navController.navigate(Routes.room(roomId)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenLocation = { navController.navigate(Routes.LOCATION_RULES) },
            )
        }

        composable(
            route = Routes.ROOM_PATTERN,
            arguments = listOf(navArgument("roomId") { type = NavType.StringType }),
        ) { entry ->
            val roomId = entry.arguments?.getString("roomId").orEmpty()
            RoomScreen(
                graph = graph,
                roomId = roomId,
                onNavigateBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenHome = {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
                onOpenLocation = { navController.navigate(Routes.LOCATION_RULES) },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                graph = graph,
                onNavigateBack = { navController.popBackStack() },
                onNavigateHome = {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
                onOpenRoom = { roomId -> navController.navigate(Routes.room(roomId)) },
                onOpenLocationRules = { navController.navigate(Routes.LOCATION_RULES) },
                onLeftRoom = { navController.popBackStack(Routes.HOME, inclusive = false) },
            )
        }

        composable(Routes.LOCATION_RULES) {
            LocationRulesScreen(
                graph = graph,
                onNavigateBack = { navController.popBackStack() },
                onAddRule = { navController.navigate(Routes.ruleEditor()) },
                onEditRule = { ruleId -> navController.navigate(Routes.ruleEditor(ruleId)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenHome = {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
                onOpenRoom = { roomId -> navController.navigate(Routes.room(roomId)) },
            )
        }

        composable(
            route = Routes.RULE_EDITOR_PATTERN,
            arguments = listOf(
                navArgument(Routes.ARG_RULE_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            RuleEditorScreen(
                graph = graph,
                ruleId = entry.arguments?.getString(Routes.ARG_RULE_ID),
                onNavigateBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenHome = {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
                onOpenRoom = { roomId -> navController.navigate(Routes.room(roomId)) },
            )
        }
        }
        }
    }
}

/**
 * Maps a navigation route onto the bottom-bar destination it represents.
 *
 * Several routes collapse onto one destination: the rule editor is reached from the rules list
 * and highlights the same nav item as it does. Mapping them to the same value is what lets the
 * pill treat an editor-to-list move as "no change" and stay put, instead of sliding to where it
 * already was.
 *
 * Returns null for a route with no bar representation - Onboarding and Settings - so callers
 * treating "no destination" and "no previous destination" the same way is correct rather than
 * merely convenient.
 */
