package com.wood.pair.ui.rules

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wood.pair.R
import com.wood.pair.data.PairGraph
import com.wood.pair.data.model.LocationRule
import com.wood.pair.data.model.TimeWindow
import com.wood.pair.ui.components.Avatars
import com.wood.pair.ui.components.PairDestination
import com.wood.pair.ui.components.PairScaffold
import com.wood.pair.ui.components.PairTopBar
import com.wood.pair.ui.rememberPairViewModel
import com.wood.pair.ui.theme.PairSurfaces
import com.wood.pair.ui.theme.pairShapes
import com.wood.pair.ui.theme.pairTypography
import com.wood.pair.wallpaper.WallpaperStore
import androidx.compose.material3.SnackbarHostState
import com.wood.pair.ui.components.rememberDestinationRouter
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.wood.pair.ui.motion.rememberPressScale

/** Page margin, matching the other screens. */
private val ScreenMargin = 20.dp

/** Fixed, unlike [androidx.compose.foundation.lazy.LazyColumn]'s day-of-week convention. */
private const val DAYS_IN_WEEK = 7

/**
 * Day initials, indexed the same way `TimeWindow.daysOfWeek` is.
 *
 * A list rather than a lookup keyed by day name, so a rule carrying a day outside the range
 * produces a wrong-looking row rather than a crash.
 */
private val DAY_ABBREVIATIONS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

@Composable
fun LocationRulesScreen(
    graph: PairGraph,
    onNavigateBack: () -> Unit,
    onAddRule: () -> Unit,
    onEditRule: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHome: () -> Unit,
    onOpenRoom: (String) -> Unit,
) {
    val viewModel = rememberPairViewModel(graph) { g ->
        RulesViewModel(
            ruleDataSource = g.locationRules,
            geofenceRegistrar = g.geofenceRegistrar,
            wallpaperStore = g.wallpaperStore,
            preferences = g.preferences,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LocationRulesContent(
        state = state,
        wallpaperStore = graph.wallpaperStore,
        onAddRule = onAddRule,
        onEditRule = onEditRule,
        onToggleRule = viewModel::setActive,
        onSelectDestination = rememberDestinationRouter(
            currentRoomId = state.currentRoomId,
            onOpenHome = onOpenHome,
            onOpenRoom = onOpenRoom,
            onOpenLocation = {},
            snackbarHostState = snackbarHostState,
        ),
        onOpenSettings = onOpenSettings,
        snackbarHostState = snackbarHostState,
    )
}

@Composable
internal fun LocationRulesContent(
    state: RulesUiState,
    wallpaperStore: WallpaperStore?,
    onAddRule: () -> Unit,
    onEditRule: (String) -> Unit,
    onToggleRule: (LocationRule, Boolean) -> Unit,
    onSelectDestination: (PairDestination) -> Unit,
    onOpenSettings: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    PairScaffold(
        topBar = {
            PairTopBar(
                displayName = state.displayName,
                avatarResId = Avatars.resIdOf(state.avatarId),
            )
        },
        selectedDestination = PairDestination.Location,
        hasRoom = state.currentRoomId != null,
        onSelectDestination = onSelectDestination,
        onOpenSettings = onOpenSettings,
        snackbarHostState = snackbarHostState,
    ) { bottomPadding ->
        LocationRulesBody(
            state = state,
            wallpaperStore = wallpaperStore,
            onAddRule = onAddRule,
            onEditRule = onEditRule,
            onToggleRule = onToggleRule,
            bottomPadding = bottomPadding,
        )
    }
}

/** The rules list, inside the shared chrome. */
@Composable
private fun LocationRulesBody(
    state: RulesUiState,
    wallpaperStore: WallpaperStore?,
    onAddRule: () -> Unit,
    onEditRule: (String) -> Unit,
    onToggleRule: (LocationRule, Boolean) -> Unit,
    bottomPadding: Dp,
) {
    val addRuleInteraction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = ScreenMargin),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(key = "header") {
                HeaderCard(
                    title = stringResource(R.string.location_wallpaper),
                    body = stringResource(R.string.location_wallpaper_body),
                )
            }
            item(key = "presets") {
                PresetsCard(
                    rules = state.rules,
                    previewLoader = wallpaperStore,
                    onEdit = { onEditRule(it) },
                    onToggle = onToggleRule,
                )
            }
        }

        Surface(
            onClick = onAddRule,
            interactionSource = addRuleInteraction,
            modifier = Modifier
                .rememberPressScale(addRuleInteraction)
                .align(Alignment.BottomEnd)
                .padding(bottom = bottomPadding + 16.dp),
            shape = MaterialTheme.pairShapes.button,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.add_rule),
                    style = MaterialTheme.pairTypography.controlLabel,
                    maxLines = 1,
                )
            }
        }
    }
}

/** The explanation of what the screen is for, as a card so it belongs to the content. */
@Composable
private fun HeaderCard(
    title: String,
    body: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.pairShapes.card,
        color = PairSurfaces.card,
        contentColor = PairSurfaces.onCard,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = title,
                    style = MaterialTheme.pairTypography.cardTitle,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = body,
                style = MaterialTheme.pairTypography.body,
                color = PairSurfaces.onCardMuted,
            )
        }
    }
}

/** The rules themselves. */
@Composable
private fun PresetsCard(
    rules: List<LocationRule>,
    previewLoader: WallpaperStore?,
    onEdit: (String) -> Unit,
    onToggle: (LocationRule, Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.pairShapes.card,
        color = PairSurfaces.card,
        contentColor = PairSurfaces.onCard,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.active_presets, rules.size),
                style = MaterialTheme.pairTypography.sectionLabel,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))

            if (rules.isEmpty()) {
                Text(
                    text = stringResource(R.string.rules_empty),
                    style = MaterialTheme.pairTypography.supporting,
                    color = PairSurfaces.onCardMuted,
                    modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
                )
            } else {
                rules.forEach { rule ->
                    RuleRow(
                        rule = rule,
                        previewLoader = previewLoader,
                        onEdit = { onEdit(rule.id) },
                        onToggle = { onToggle(rule, it) },
                    )
                }
            }
        }
    }
}

/**
 * One rule: its wallpaper, its name, and the two things that can be done with it.
 *
 * The preview is loaded rather than stored on the rule so a list of rules does not carry a
 * decoded bitmap per row; the load is keyed on the wallpaper reference, so changing a rule's
 * image reloads it and nothing else does.
 */
@Composable
private fun RuleRow(
    rule: LocationRule,
    previewLoader: WallpaperStore?,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val preview by produceState<Bitmap?>(initialValue = null, rule.wallpaperUri, previewLoader) {
        value = if (rule.wallpaperUri.isBlank() || previewLoader == null) {
            null
        } else {
            previewLoader.loadPreview(rule.wallpaperUri)
        }
    }

    val summary = stringResource(
        R.string.within_radius_days,
        rule.radiusMeters.toInt().toString(),
        daySummary(rule.timeWindow),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        preview?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(MaterialTheme.pairShapes.image),
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Top,
        ) {
            Text(
                text = rule.label.ifBlank { stringResource(R.string.rules_location) },
                style = MaterialTheme.pairTypography.itemTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = summary,
                style = MaterialTheme.pairTypography.supporting,
                color = PairSurfaces.onCardMuted,
                // One line, always. A row that grows to two lines pushes every row below it
                // down, so one long label re-flows the whole list; an ellipsis keeps the rows
                // a fixed height and the list scannable.
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(8.dp))

        Switch(
            checked = rule.isActive,
            onCheckedChange = onToggle,
        )

        Spacer(Modifier.width(8.dp))

        SmallFloatingActionButton(
            onClick = onEdit,
            modifier = Modifier.size(48.dp),
            shape = MaterialTheme.pairShapes.iconButton,
            // Filled, not tonal: this is the only way into a rule, and on a card that is already
            // a filled surface it needs the contrast to read as an action.
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = stringResource(R.string.rules_edit),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * A rule's time restriction, in one line.
 *
 * "All days" and "no restriction" are stated rather than implied by an empty set, because an
 * empty set is the one reading that would otherwise be ambiguous between the two.
 */
@Composable
private fun daySummary(window: TimeWindow?): String {
    if (window == null) return stringResource(R.string.rules_time_any)
    if (window.daysOfWeek.size == DAYS_IN_WEEK) return stringResource(R.string.all_days)
    if (window.daysOfWeek.isEmpty()) return stringResource(R.string.rules_time_any)
    return window.daysOfWeek.sorted().joinToString(separator = ", ") { day ->
        DAY_ABBREVIATIONS.getOrElse(day) { day.toString() }
    }
}
