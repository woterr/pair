package com.wood.pair.ui.rules

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wood.pair.R
import com.wood.pair.data.PairGraph
import com.wood.pair.data.model.LocationRule
import com.wood.pair.ui.components.Avatars
import com.wood.pair.ui.components.PairDestination
import com.wood.pair.ui.components.PairScaffold
import com.wood.pair.ui.components.PairTopBar
import com.wood.pair.ui.components.VerticalGap
import com.wood.pair.ui.rememberPairViewModel
import com.wood.pair.ui.theme.LocalMotionScheme
import com.wood.pair.ui.theme.PairSurfaces
import com.wood.pair.ui.theme.pairShapes
import com.wood.pair.ui.theme.pairTypography
import com.wood.pair.wallpaper.WallpaperStore
import java.util.Locale
import androidx.compose.material3.SnackbarHostState
import com.wood.pair.ui.components.rememberDestinationRouter
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.wood.pair.ui.motion.rememberPressScale
import com.wood.pair.ui.components.PairNameField
import androidx.compose.material.icons.filled.Image
import androidx.compose.foundation.clickable
import androidx.compose.material3.ripple
import com.wood.pair.ui.theme.Space
import com.wood.pair.data.model.WallpaperTarget
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow

/**
 * The one letter per day, as the comp draws them.
 *
 * Indexed the same way `TimeWindow.daysOfWeek` is, so the chips and the stored set cannot fall
 * out of step. `M` appears twice and `T` twice, which is why the full name is kept alongside for
 * the accessibility label.
 */
private val DayLetters = listOf("S", "M", "T", "W", "T", "F", "S")

/** Full day names, for the same indexing. */
private val DayNames = listOf(
    "Sunday",
    "Monday",
    "Tuesday",
    "Wednesday",
    "Thursday",
    "Friday",
    "Saturday",
)

/** Which of the two time fields the picker dialog is currently editing. */
private enum class TimeField { Start, End }

@Composable
fun RuleEditorScreen(
    graph: PairGraph,
    ruleId: String?,
    onNavigateBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHome: () -> Unit,
    onOpenRoom: (String) -> Unit,
) {
    val application = LocalContext.current.applicationContext as Application
    val context = LocalContext.current
    val viewModel = rememberPairViewModel(graph) { g ->
        RuleEditorViewModel(
            application = application,
            ruleDataSource = g.locationRules,
            geofenceRegistrar = g.geofenceRegistrar,
            wallpaperStore = g.wallpaperStore,
            locationPicker = g.locationRulePicker,
            preferences = g.preferences,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Loading is keyed on the id rather than run once: the screen is reused for both "add" and
    // "edit", and a remembered-once load would leave a stale rule on screen.
    LaunchedEffect(ruleId) { viewModel.load(ruleId) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.any { it }) viewModel.useCurrentLocation()
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? -> uri?.let(viewModel::onWallpaperPicked) }

    val snackbarHostState = remember { SnackbarHostState() }

    RuleEditorContent(
        state = state,
        hasLocationPermission = viewModel.hasLocationPermission(),
        wallpaperStore = graph.wallpaperStore,
        onLabelChange = viewModel::onLabelChange,
        onUseCurrentLocation = {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        },
        onOpenBackgroundSettings = { context.openBackgroundLocationSettings() },
        onRadiusChange = viewModel::onRadiusChange,
        onTimeRestrictionChange = viewModel::onTimeRestrictionChange,
        onStartMinuteChange = viewModel::onStartMinuteChange,
        onEndMinuteChange = viewModel::onEndMinuteChange,
        onToggleDay = viewModel::toggleDay,
        onWallpaperTargetChange = viewModel::onWallpaperTargetChange,
        onPickWallpaper = {
            imagePicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
        onSave = { viewModel.save(onNavigateBack) },
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

/**
 * The rule editor, from plain state.
 *
 * The whole screen, chrome included, so a render of this is what a user actually sees. The
 * permission flag and the wallpaper loader are passed in rather than read here, so the screen
 * renders in a preview or a screenshot test with no platform behind it.
 */
@Composable
internal fun RuleEditorContent(
    state: RuleEditorUiState,
    hasLocationPermission: Boolean,
    wallpaperStore: WallpaperStore?,
    onLabelChange: (String) -> Unit,
    onUseCurrentLocation: () -> Unit,
    onOpenBackgroundSettings: () -> Unit,
    onRadiusChange: (Float) -> Unit,
    onTimeRestrictionChange: (Boolean) -> Unit,
    onStartMinuteChange: (Int) -> Unit,
    onEndMinuteChange: (Int) -> Unit,
    onToggleDay: (Int) -> Unit,
    onPickWallpaper: () -> Unit,
    onWallpaperTargetChange: (WallpaperTarget) -> Unit,
    onSave: () -> Unit,
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
        RuleEditorBody(
            state = state,
            hasLocationPermission = hasLocationPermission,
            wallpaperStore = wallpaperStore,
            onLabelChange = onLabelChange,
            onUseCurrentLocation = onUseCurrentLocation,
            onOpenBackgroundSettings = onOpenBackgroundSettings,
            onRadiusChange = onRadiusChange,
            onTimeRestrictionChange = onTimeRestrictionChange,
            onStartMinuteChange = onStartMinuteChange,
            onEndMinuteChange = onEndMinuteChange,
            onToggleDay = onToggleDay,
            onPickWallpaper = onPickWallpaper,
            onWallpaperTargetChange = onWallpaperTargetChange,
            onSave = onSave,
            bottomPadding = bottomPadding,
        )
    }
}

/** The editor's form, inside the shared chrome. */
@Composable
private fun RuleEditorBody(
    state: RuleEditorUiState,
    hasLocationPermission: Boolean,
    wallpaperStore: WallpaperStore?,
    onLabelChange: (String) -> Unit,
    onUseCurrentLocation: () -> Unit,
    onOpenBackgroundSettings: () -> Unit,
    onRadiusChange: (Float) -> Unit,
    onTimeRestrictionChange: (Boolean) -> Unit,
    onStartMinuteChange: (Int) -> Unit,
    onEndMinuteChange: (Int) -> Unit,
    onToggleDay: (Int) -> Unit,
    onPickWallpaper: () -> Unit,
    onWallpaperTargetChange: (WallpaperTarget) -> Unit,
    onSave: () -> Unit,
    bottomPadding: Dp,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        PlaceField(
            label = state.label,
            onLabelChange = onLabelChange,
        )

        if (state.hasLocation) {
            // No extra gap of its own. The coordinates are a detail *of* the place field above
            // them, and `Arrangement.spacedBy` already sets the rhythm — adding another spacer
            // here would make this one gap larger than every other, which is exactly the
            // inconsistency a single rhythm is meant to remove.
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedPill(
                    text = stringResource(R.string.lat_value, String.format(Locale.US, "%.4f", state.latitude!!)),
                )
                OutlinedPill(
                    text = stringResource(R.string.long_value, String.format(Locale.US, "%.4f", state.longitude!!)),
                )
            }
        }

        TonalButton(
            onClick = onUseCurrentLocation,
            icon = {
                Icon(
                    imageVector = Icons.Filled.MyLocation,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            },
            label = stringResource(R.string.set_current_location),
        )

        LocationAccessCard(
            hasPermission = hasLocationPermission,
            onGrant = onUseCurrentLocation,
            onOpenSettings = onOpenBackgroundSettings,
        )

        GeofenceRadiusCard(
            radiusMeters = state.radiusMeters,
            onRadiusChange = onRadiusChange,
        )

        TimeRestrictionCard(
            hasTimeRestriction = state.hasTimeRestriction,
            onTimeRestrictionChange = onTimeRestrictionChange,
            startMinute = state.startMinute,
            endMinute = state.endMinute,
            onStartMinuteChange = onStartMinuteChange,
            onEndMinuteChange = onEndMinuteChange,
            daysOfWeek = state.daysOfWeek,
            onToggleDay = onToggleDay,
        )

        WallpaperCard(
            reference = state.wallpaperReference,
            loader = wallpaperStore,
            target = state.wallpaperTarget,
            onPick = onPickWallpaper,
            onTargetChange = onWallpaperTargetChange,
        )

        // The error sits directly above the button it blocks, so the reason the save did not
        // happen is next to the control that would have caused it.
        state.error?.let { error ->
            VerticalGap(6.dp)
            Text(
                text = stringResource(error.asStringRes()),
                style = MaterialTheme.pairTypography.supporting,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            enabled = state.canSave,
            shape = MaterialTheme.pairShapes.button,
            contentPadding = PaddingValues(vertical = 14.dp),
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = stringResource(R.string.save_rule),
                    style = MaterialTheme.pairTypography.controlLabel,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The rule's name.
 *
 * The same field as the display name in Settings, not a generic one: it is the same kind of
 * thing — a short label for something the person will recognise later — and two different
 * treatments of it on two screens is the kind of difference nobody asks for and everybody
 * notices.
 *
 * The label sits above the field and does not move. Material's floating label would migrate into
 * the border on focus, which is a second copy of a heading that is already on screen.
 */
/**
 * The place name, using the app's one name field.
 *
 * The edit pencil that used to sit in this field's trailing slot is gone. It was a second control
 * for an action the field does not have a separate state for: this field is live from the moment
 * the editor opens, there is nothing to "enter" before typing, and the row it sits in is already
 * tappable. An icon that looks like a button and is not one is worse than no icon.
 *
 * The box itself is now [PairNameField], the same component Settings uses for the display name,
 * so the two fields are identical rather than merely similar.
 */
@Composable
private fun PlaceField(
    label: String,
    onLabelChange: (String) -> Unit,
) {
    Column {
        Text(
            text = stringResource(R.string.place_name),
            style = MaterialTheme.pairTypography.sectionLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Space.labelGap))
        PairNameField(
            value = label,
            onValueChange = onLabelChange,
            placeholder = stringResource(R.string.rules_location_pick),
        )
    }
}

/** A read-only value shown as an outlined pill, as the comp shows coordinates. */
@Composable
private fun OutlinedPill(text: String) {
    Surface(
        shape = MaterialTheme.pairShapes.pill,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.pairTypography.supporting,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/**
 * A filled tonal action with a leading icon.
 *
 * Written once here rather than repeated: the editor has three of these and they have to match.
 */
@Composable
private fun TonalButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: String,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.pairShapes.button,
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.pairTypography.controlLabel,
                maxLines = 1,
            )
        }
    }
}

/**
 * Why location access is needed, and the one action that resolves it.
 *
 * A single card with one button, whose label changes with what's actually missing. Two cards —
 * one for foreground permission, one for background — would mean the user reads the same
 * explanation twice and has to work out which of the two applies to them.
 */
@Composable
private fun LocationAccessCard(
    hasPermission: Boolean,
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Card2 {
        Text(
            text = stringResource(R.string.location_access_required),
            style = MaterialTheme.pairTypography.sectionLabel,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(10.dp))
        Text(
            // The explanation follows the state: a user with foreground access is not being asked
            // for foreground access, and being told they are is the kind of detail that makes a
            // person stop reading the rest of the screen.
            text = stringResource(
                if (hasPermission) {
                    R.string.rules_background_note
                } else {
                    R.string.rules_permission_rationale
                },
            ),
            style = MaterialTheme.pairTypography.supporting,
            color = PairSurfaces.onCardMuted,
        )
        Spacer(Modifier.height(12.dp))
        // A real button, not a label with padding. This is the only control that can change the
        // outcome, and it needs the 48dp target, the fill and the ink contrast that says so.
        Button(
            onClick = if (hasPermission) onOpenSettings else onGrant,
            shape = MaterialTheme.pairShapes.button,
        ) {
            Text(
                text = stringResource(
                    if (hasPermission) {
                        R.string.rules_open_background_settings
                    } else {
                        R.string.rules_grant_location
                    },
                ),
                style = MaterialTheme.pairTypography.controlLabel,
                maxLines = 1,
            )
        }
    }
}

/** How big the fence is, with the scale the comp labels it at. */
@Composable
private fun GeofenceRadiusCard(
    radiusMeters: Float,
    onRadiusChange: (Float) -> Unit,
) {
    Card2 {
        Text(
            text = stringResource(R.string.geofence_radius),
            style = MaterialTheme.pairTypography.sectionLabel,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(10.dp))
        Slider(
            value = radiusMeters,
            onValueChange = onRadiusChange,
            valueRange = LocationRule.MIN_RADIUS_METERS..LocationRule.MAX_RADIUS_METERS,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            RadiusLabel("200m")
            RadiusLabel("500m")
            RadiusLabel("1km")
        }
    }
}

@Composable
private fun RadiusLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.pairTypography.supporting,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The optional time restriction: on/off, a window, and the days it applies to.
 *
 * The window is two read-only fields rather than two editable ones. Editing a time is a dialog's
 * job — a pair of dropdowns is slower to use and worse to look at than the platform picker, and
 * it cannot show the clock face people actually recognise.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeRestrictionCard(
    hasTimeRestriction: Boolean,
    onTimeRestrictionChange: (Boolean) -> Unit,
    startMinute: Int,
    endMinute: Int,
    onStartMinuteChange: (Int) -> Unit,
    onEndMinuteChange: (Int) -> Unit,
    daysOfWeek: Set<Int>,
    onToggleDay: (Int) -> Unit,
) {
    var editing by remember { mutableStateOf<TimeField?>(null) }

    Card2 {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.time_restriction),
                style = MaterialTheme.pairTypography.sectionLabel,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            // At the end of the row, not beside the label. A switch belongs to the whole card
            // and reads as the card's state; pinned next to the words it reads as a property
            // of the word "Time restriction", which is not what it controls.
            Switch(
                checked = hasTimeRestriction,
                onCheckedChange = onTimeRestrictionChange,
            )
        }

        Spacer(Modifier.height(10.dp))
        // Everything below the switch stays on screen when the restriction is off, dimmed
        // rather than hidden. Hiding it made the card change height the moment it was touched,
        // which moved the day chips out from under the finger that was about to hit them, and it
        // hid the reason a rule was inactive behind a switch.
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TimeField(
                label = stringResource(R.string.start_time),
                minute = startMinute,
                enabled = hasTimeRestriction,
                onClick = { editing = TimeField.Start },
                modifier = Modifier.weight(1f),
            )
            TimeField(
                label = stringResource(R.string.end_time),
                minute = endMinute,
                enabled = hasTimeRestriction,
                onClick = { editing = TimeField.End },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.active_days),
            style = MaterialTheme.pairTypography.supporting,
            color = if (hasTimeRestriction) {
                PairSurfaces.onCardMuted
            } else {
                PairSurfaces.onCardMuted.copy(alpha = 0.5f)
            },
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            DayLetters.forEachIndexed { index, letter ->
                val day = index + 1
                DayChip(
                    letter = letter,
                    dayName = DayNames[index],
                    selected = day in daysOfWeek,
                    // Muted and inert together: a control that looks live but does nothing is
                    // worse than one that is plainly off.
                    enabled = hasTimeRestriction,
                    onClick = { onToggleDay(day) },
                )
            }
        }
    }

    val field = editing
    if (field != null) {
        val initial = when (field) {
            TimeField.Start -> startMinute
            TimeField.End -> endMinute
        }
        val pickerState = rememberTimePickerState(
            initialHour = (initial / 60) % 24,
            initialMinute = initial % 60,
            is24Hour = false,
        )
        TimePickerDialog(
            onDismissRequest = { editing = null },
            title = {
                Text(
                    text = stringResource(R.string.rules_enter_time),
                    modifier = Modifier.padding(start = 24.dp, top = 16.dp),
                    style = MaterialTheme.pairTypography.cardTitle,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val chosen = pickerState.hour * 60 + pickerState.minute
                        when (field) {
                            TimeField.Start -> onStartMinuteChange(chosen)
                            TimeField.End -> onEndMinuteChange(chosen)
                        }
                        editing = null
                    },
                ) { Text(stringResource(R.string.rules_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) {
                    Text(stringResource(R.string.join_cancel))
                }
            },
        ) {
            TimePicker(state = pickerState)
        }
    }
}

/**
 * One day of the week.
 *
 * Selected days are the app's own star — the same mark welded over the "i" in the wordmark, so
 * the one thing the design means by "this is on" is one thing everywhere. The change between
 * circle and star is animated rather than swapped: a swap changes the silhouette on a single
 * frame, and at 48dp with a finger on it that reads as a glitch. A cross-fade plus a small
 * spring on the mark reads as the day *becoming* selected.
 */
@Composable
private fun DayChip(
    letter: String,
    dayName: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val motion = LocalMotionScheme.current
    val enabledAlpha = if (enabled) 1f else 0.45f
    // Fast, both axes: a 44dp chip is a small component, and this drives a shape *and* an alpha,
    // so the spatial and effects springs run together. No reduced-motion branch, because
    // `fastSpatialSpec` already resolves to `snap()` — the same dead override that made press
    // feedback ignore the preference.
    val selectedAmount by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = motion.fastSpatialSpec(),
        label = "daySelected",
    )

    Box(
        modifier = Modifier
            .size(44.dp)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.Checkbox,
                onClick = onClick,
            )
            .semantics { contentDescription = dayName }
            .graphicsLayer { alpha = enabledAlpha },
        contentAlignment = Alignment.Center,
    ) {
        // Unselected circle container
        Box(
            modifier = Modifier
                .size(40.dp)
                .graphicsLayer { alpha = 1f - selectedAmount }
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = letter,
                style = MaterialTheme.pairTypography.controlLabel,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        // Selected flowerish shape container with visible day letter
        Box(
            modifier = Modifier
                .size(42.dp)
                .graphicsLayer {
                    val scale = 0.85f + 0.15f * selectedAmount
                    alpha = selectedAmount
                    scaleX = scale
                    scaleY = scale
                }
                .clip(com.wood.pair.ui.shape.ScallopedShape(petals = 12, depth = 0.14f))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = letter,
                style = MaterialTheme.pairTypography.controlLabel,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

/** A read-only time that opens the platform picker when tapped. */
@Composable
private fun TimeField(
    label: String,
    minute: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    // A container one step *below* the card, not the same surface again. Two elements with the
    // same fill and only a corner radius between them read as one shape with something
    // overlapping it, which is not what a pair of side-by-side fields is.
    val container = if (enabled) {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
    }
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        // 0.98, not the 0.96 a button gets. This surface is dressed as a text field, and a field
        // that visibly squashes reads as broken rather than as pressed; the acknowledgement is
        // there, it is just not the gesture.
        modifier = modifier.rememberPressScale(interactionSource, pressedScale = 0.98f),
        shape = MaterialTheme.pairShapes.field,
        color = container,
        contentColor = if (enabled) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = label,
                style = MaterialTheme.pairTypography.supporting,
                color = PairSurfaces.onCardMuted,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatMinuteOfDay(minute),
                style = MaterialTheme.pairTypography.itemTitle,
                maxLines = 1,
            )
        }
    }
}

/** The wallpaper, with a preview at the aspect ratio a phone actually uses. */
@Composable
private fun WallpaperCard(
    reference: String,
    loader: WallpaperStore?,
    target: WallpaperTarget,
    onPick: () -> Unit,
    onTargetChange: (WallpaperTarget) -> Unit,
) {
    Card2 {
        Text(
            text = stringResource(R.string.wallpaper),
            style = MaterialTheme.pairTypography.sectionLabel,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(10.dp))
        WallpaperPreview(
            reference = reference,
            loader = loader,
            onPick = onPick,
        )
        Spacer(Modifier.height(Space.labelGap))
        WallpaperTargetSelector(selected = target, onSelect = onTargetChange)
    }
}

/**
 * Home screen, lock screen, or both.
 *
 * A segmented control rather than a switch or a dropdown, because it is three named choices and
 * only one can be true - the same reason the theme picker is a track rather than a list. A switch
 * could only express "on or off" and would need a second control to say *which*.
 *
 * The lock screen is a separate surface from the home screen on Android, and the two are set
 * independently, so the choice is real and consequential rather than cosmetic. Defaults to
 * [WallpaperTarget.Home], the less intrusive of the two.
 */
@Composable
private fun WallpaperTargetSelector(
    selected: WallpaperTarget,
    onSelect: (WallpaperTarget) -> Unit,
) {
    val options = listOf(
        WallpaperTarget.Home to R.string.wallpaper_target_home,
        WallpaperTarget.Lock to R.string.wallpaper_target_lock,
        WallpaperTarget.Both to R.string.wallpaper_target_both,
    )

    // No animation spec here on purpose: `SegmentedButton` is a Material component and already
    // drives its own indicator from the theme's motion scheme. Reaching in to hand it a spec would
    // be the same class of mistake as passing one to an M3 button - the component is the thing
    // that knows how fast *its own* selection should move.
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (target, labelRes) ->
            SegmentedButton(
                selected = selected == target,
                onClick = { onSelect(target) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                modifier = Modifier.weight(1f),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    inactiveContainerColor = PairSurfaces.card,
                    inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                icon = {},
            ) {
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.pairTypography.status,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun WallpaperPreview(
    reference: String,
    loader: WallpaperStore?,
    onPick: () -> Unit,
) {
    val preview by produceState<android.graphics.Bitmap?>(
        initialValue = null,
        reference,
        loader,
    ) {
        value = if (reference.isBlank() || loader == null) null else loader.loadPreview(reference)
    }

    val bitmap = preview
    val pickInteraction = remember { MutableInteractionSource() }
    if (bitmap != null) {
        // Tappable when a wallpaper is already set, and that is the fix.
        //
        // The two branches used to be a plain `Image` and a clickable "unset" surface, so the
        // moment a rule had a wallpaper its preview became inert: an active preset could be
        // opened, its image looked at, and then there was no way to change it without deleting the
        // rule and building it again. The image is now the same control the empty state is.
        //
        // The "Change" badge is what makes it discoverable. An image with no affordance at all
        // would still be tappable and still be a guess.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(615f / 225f)
                .clip(MaterialTheme.pairShapes.image)
                .clickable(
                    interactionSource = pickInteraction,
                    indication = ripple(),
                    onClick = onPick,
                ),
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.cd_wallpaper_preview, reference),
                contentScale = ContentScale.Crop,
                // 615 x 225 is the comp's own preview, which is close enough to a modern phone's
                // aspect that the crop the user sees here is the crop they will get.
                modifier = Modifier.fillMaxSize(),
            )

            // Bottom-start, so it never covers the face in a portrait wallpaper, which is what
            // people crop to.
            //
            // The badge is scrim-and-fixed-colour, which is the problem: on a dark wallpaper the
            // "Change" label was dark-on-dark and effectively invisible, and the control that
            // makes a set wallpaper replaceable became undiscoverable on exactly the images most
            // likely to be chosen for a lock screen. The foreground is now chosen from the
            // image's own brightness, the same rule the status bar and notification icons use.
            WallpaperChangeBadge(bitmap = bitmap)
        }
    } else {
        Surface(
            onClick = onPick,
            interactionSource = pickInteraction,
            shape = MaterialTheme.pairShapes.image,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                // Barely there. This is a large empty area, and a 4% shrink on something the width
                // of the screen is a lurch, not an acknowledgement.
                .rememberPressScale(pickInteraction, pressedScale = 0.995f)
                .fillMaxWidth()
                .aspectRatio(615f / 225f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.rules_wallpaper_unset),
                    style = MaterialTheme.pairTypography.supporting,
                )
            }
        }
    }
}

/**
 * The "Change" affordance on a set wallpaper, legible on any image.
 *
 * ## Why the colour is measured rather than chosen
 *
 * A fixed scrim and a fixed foreground only work on an average image. On a dark wallpaper the
 * label went dark-on-dark and disappeared, which quietly removed the only affordance for
 * replacing a wallpaper - and dark images are exactly the ones people pick for a lock screen.
 *
 * So the scrim's *own* luminance is sampled from the image, once, on a 1x1 decode, and the
 * foreground is picked from the opposite pole. That is the same rule the platform uses for status
 * bar and notification icons, and for the same reason: contrast has to be decided against the
 * background it will be drawn on, which here is arbitrary.
 *
 * A 1x1 decode is a few hundred bytes and runs once per selection, not per frame. The result is
 * remembered against the bitmap so recomposition does not resample.
 */
@Composable
private fun BoxScope.WallpaperChangeBadge(bitmap: android.graphics.Bitmap) {
    val dark = remember(bitmap) { bitmap.averageLuminanceIsDark() }

    Surface(
        shape = MaterialTheme.pairShapes.pill,
        // A near-opaque scrim, so the sampled polarity is about the image and not about us. A
        // translucent one would let a bright patch under a dark one flip the contrast back.
        color = if (dark) Color.Black.copy(alpha = 0.62f) else Color.White.copy(alpha = 0.82f),
        contentColor = if (dark) Color.White else Color.Black,
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(Space.panelPadding),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Image,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(R.string.rules_wallpaper_change),
                style = MaterialTheme.pairTypography.status,
            )
        }
    }
}

/**
 * Whether this image is dark enough to need light text on it.
 *
 * Perceptual luminance rather than an average of the channels: a saturated blue and a saturated
 * yellow have the same channel average and completely different perceived brightness, and one of
 * them will defeat a naive threshold.
 *
 * The 0.5 pivot on a 0..1 linear scale is the same one the platform's own contrast helpers use
 * for deciding light-on-dark, rather than a value tuned to look right on one screenshot.
 */
private fun android.graphics.Bitmap.averageLuminanceIsDark(): Boolean {
    val sample = runCatching {
        val scaled = android.graphics.Bitmap.createScaledBitmap(this, 1, 1, true)
        val pixel = IntArray(1)
        scaled.getPixels(pixel, 0, 1, 0, 0, 1, 1)
        // getPixels has already sRGB-decoded for us, so undo the transfer curve per channel to
        // get something linear to weight.
        pixel[0]
    }.getOrNull() ?: return true

    val red = srgbToLinear((sample shr 16) and 0xFF)
    val green = srgbToLinear((sample shr 8) and 0xFF)
    val blue = srgbToLinear(sample and 0xFF)
    val luminance = 0.2126f * red + 0.7152f * green + 0.0722f * blue
    return luminance < 0.5f
}

/** One channel of sRGB, normalised, as linear light. */
private fun srgbToLinear(channel: Int): Float {
    val c = channel / 255f
    return if (c <= 0.04045f) c / 12.92f else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
}

/** The editor's one card shape, so every panel here is the same surface. */
@Composable
private fun Card2(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.pairShapes.card,
        color = PairSurfaces.card,
        contentColor = PairSurfaces.onCard,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start,
        ) {
            content()
        }
    }
}

/**
 * A minute-of-day as a 12-hour time.
 *
 * Formatted by hand rather than with a locale-aware formatter because a *stored* time window is
 * not a moment in time: it must read the same for everyone, and "24:00" is a legitimate end that
 * a formatter would either reject or turn into midnight of another day.
 */
private fun formatMinuteOfDay(minute: Int): String {
    val hours = minute / 60 % 24
    val suffix = if (hours < 12) "AM" else "PM"
    val display = when {
        hours == 0 -> 12
        hours > 12 -> hours - 12
        else -> hours
    }
    return String.format(Locale.US, "%d:%02d %s", display, minute % 60, suffix)
}

/**
 * Opens this app's details page, where background location can be granted.
 *
 * `runCatching` because a device can lack the activity, and a form that crashes is worse than one
 * that leaves the user where they were.
 */
private fun Context.openBackgroundLocationSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", packageName, null))
    runCatching { startActivity(intent) }
}

/**
 * The user-facing message for each failure.
 *
 * Total by construction: every value the error type can hold has a line, so there is no path
 * where saving fails silently.
 */
private fun RuleEditorError.asStringRes(): Int = when (this) {
    RuleEditorError.LocationRequired -> R.string.rules_needs_location
    RuleEditorError.LocationUnavailable -> R.string.rules_location_unavailable
    RuleEditorError.WallpaperRequired -> R.string.rules_needs_wallpaper
    RuleEditorError.GeofencePermissionMissing -> R.string.rules_permission_needed
    RuleEditorError.GeofenceFailed -> R.string.rules_geofence_failed
    RuleEditorError.WallpaperFailed -> R.string.error_wallpaper_failed
}
