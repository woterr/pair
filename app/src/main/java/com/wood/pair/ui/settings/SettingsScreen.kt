package com.wood.pair.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wood.pair.R
import com.wood.pair.data.PairGraph
import com.wood.pair.debug.PeerDevices
import com.wood.pair.ui.components.AvatarPicker
import com.wood.pair.ui.components.Avatars
import com.wood.pair.ui.components.PairBottomBar
import com.wood.pair.ui.components.PairDestination
import com.wood.pair.ui.components.SectionHeader
import com.wood.pair.ui.rememberPairViewModel
import com.wood.pair.ui.room.RoomLeaveDialog
import com.wood.pair.ui.theme.PairSurfaces
import com.wood.pair.ui.theme.ThemeMode
import com.wood.pair.ui.theme.pairMotion
import com.wood.pair.ui.theme.pairShapes
import com.wood.pair.ui.theme.pairTypography
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.layout.navigationBarsPadding
import com.wood.pair.ui.components.rememberDestinationRouter
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.wood.pair.ui.motion.rememberPressScale
import com.wood.pair.ui.components.PairNameField

/**
 * The permission and battery state the Location section reports.
 *
 * Both are owned by the platform and can change while the app is in the background, so they are
 * re-read on resume rather than captured once.
 */
private data class PermissionAndBatteryState(
    val hasLocation: Boolean,
    val batteryUnrestricted: Boolean,
)

/** Room for the shared bottom bar, which Settings carries like every other screen. */
private val BottomBarClearance = 104.dp

@Composable
fun SettingsScreen(
    graph: PairGraph,
    onNavigateBack: () -> Unit,
    onNavigateHome: () -> Unit,
    onOpenRoom: (String) -> Unit,
    onOpenLocationRules: () -> Unit,
    onLeftRoom: (String) -> Unit,
) {
    val viewModel = rememberPairViewModel(graph) { g ->
        SettingsViewModel(
            preferences = g.preferences,
            auth = g.authRepository,
            roomRepository = g.roomRepository,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val notificationsEnabled = remember {
        androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val canPromote = remember { com.wood.pair.notifications.LiveUpdateNotifier.canBePromoted(context) }
    val liveUpdatesSupported = com.wood.pair.notifications.LiveUpdateNotifier.liveUpdateSupported

    val permissionState = rememberPermissionAndBatteryState()

    val locationStatus = if (permissionState.hasLocation) {
        stringResource(R.string.settings_permission_granted)
    } else {
        stringResource(R.string.settings_permission_denied)
    }

    var leaveDialogVisible by remember { mutableStateOf(false) }

    SettingsContent(
        state = state,
        locationStatus = locationStatus,
        batteryUnrestricted = permissionState.batteryUnrestricted,
        notificationsEnabled = notificationsEnabled,
        canPromote = canPromote,
        liveUpdatesSupported = liveUpdatesSupported,
        onStartEditing = viewModel::startRename,
        onDraftChange = viewModel::onRenameDraftChange,
        onCommitRename = viewModel::confirmRename,
        onCancelRename = viewModel::cancelRename,
        onSelectAvatar = viewModel::setAvatar,
        onSelectTheme = viewModel::setThemeMode,
        onDynamicColorChange = viewModel::setDynamicColor,
        onAmoledChange = viewModel::setAmoledDark,
        onLiveUpdateChange = viewModel::setLiveUpdateEnabled,
        onOpenNotificationSettings = { context.openNotificationSettings() },
        onOpenBatterySettings = { context.openBatterySettings() },
        onOpenLocationSettings = { context.openLocationSettings() },
        // Leaving is destructive and cannot be undone from the room, so it is confirmed.
        onLeaveRoom = {
            if (state.currentRoomId != null) {
                leaveDialogVisible = true
            }
        },
        onNavigateBack = onNavigateBack,
        onNavigateHome = onNavigateHome,
        onOpenRoom = onOpenRoom,
        onOpenLocationRules = onOpenLocationRules,
    )

    if (leaveDialogVisible) {
        RoomLeaveDialog(
            isLeaving = state.isLeaving,
            onConfirm = {
                leaveDialogVisible = false
                state.currentRoomId?.let { viewModel.leaveRoom(onLeftRoom) }
            },
            onDismiss = { leaveDialogVisible = false },
        )
    }

    if (state.error != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text(stringResource(R.string.error_generic)) },
            text = {
                Text(
                    when (state.error) {
                        SettingsError.LeaveFailed -> stringResource(R.string.error_leave_failed)
                        SettingsError.NameRequired -> stringResource(R.string.onboarding_name_required)
                        SettingsError.NameTooLong -> stringResource(R.string.onboarding_name_too_long)
                        null -> ""
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissError) {
                    Text(stringResource(R.string.error_dismiss))
                }
            },
        )
    }
}

/**
 * The settings screen's content, from plain state.
 *
 * Everything that comes from the platform — permission and battery state, whether notifications
 * are enabled, whether this OS can promote Live Updates — is passed in rather than read here, so
 * that the screen renders identically in a preview or a screenshot test with no device behind it.
 */
@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    locationStatus: String,
    batteryUnrestricted: Boolean,
    notificationsEnabled: Boolean,
    canPromote: Boolean,
    liveUpdatesSupported: Boolean,
    onStartEditing: () -> Unit,
    onDraftChange: (String) -> Unit,
    onCommitRename: () -> Unit,
    onCancelRename: () -> Unit,
    onSelectAvatar: (String) -> Unit,
    onSelectTheme: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onAmoledChange: (Boolean) -> Unit,
    onLiveUpdateChange: (Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onLeaveRoom: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateHome: () -> Unit,
    onOpenRoom: (String) -> Unit,
    onOpenLocationRules: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Settings has no shared top bar, so nothing else in the screen absorbs the
            // status-bar inset. Without this the title sits underneath the clock.
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // Room for the shared bottom bar, which Settings carries like every other screen.
                .padding(bottom = BottomBarClearance),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val backInteraction = remember { MutableInteractionSource() }
                Surface(
                    onClick = onNavigateBack,
                    interactionSource = backInteraction,
                    shape = MaterialTheme.pairShapes.pill,
                    color = PairSurfaces.card,
                    contentColor = PairSurfaces.onCard,
                    // The deepest press in the app. It is a 48dp control doing one thing, so
                    // there is nothing else on screen to acknowledge the touch for it.
                    modifier = Modifier
                        .rememberPressScale(backInteraction, pressedScale = 0.92f)
                        .size(width = 64.dp, height = 48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.pairTypography.screenTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
            }

            Spacer(Modifier.size(28.dp))

            // A real gap between the label and the field, and it is the *same* gap whatever
            // state the field is in. The label does not move when the field becomes editable,
            // and the field does not jump when it stops being editable: the two are siblings
            // with a fixed space between them, not a label that migrates into a border.
            Text(
                text = stringResource(R.string.settings_display_name),
                style = MaterialTheme.pairTypography.sectionLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(10.dp))
            NameField(
                displayName = state.displayName,
                isEditing = state.isRenaming,
                draft = state.renameDraft,
                onStartEditing = onStartEditing,
                onDraftChange = onDraftChange,
                onCommit = onCommitRename,
                onCancel = onCancelRename,
            )

            Spacer(Modifier.size(24.dp))

            AvatarPicker(
                selectedId = Avatars.selectedId(state.avatarId),
                onSelect = onSelectAvatar,
            )

            Spacer(Modifier.size(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.size(24.dp))

            SectionHeader(
                icon = Icons.Outlined.Palette,
                text = stringResource(R.string.settings_appearance),
            )
            Spacer(Modifier.size(24.dp))

            ThemeModeSelector(
                selected = state.themeMode,
                onSelect = onSelectTheme,
            )

            if (supportsDynamicColorSetting()) {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_dynamic_color),
                    summary = stringResource(R.string.settings_dynamic_color_summary),
                    checked = state.dynamicColor,
                    onCheckedChange = onDynamicColorChange,
                )
            }
            SettingsSwitchRow(
                title = stringResource(R.string.settings_amoled),
                summary = stringResource(R.string.settings_amoled_summary),
                checked = state.amoledDark,
                onCheckedChange = onAmoledChange,
            )

            Spacer(Modifier.size(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.size(24.dp))

            SectionHeader(
                icon = Icons.Outlined.NotificationsActive,
                text = stringResource(R.string.settings_live_updates),
            )
            Spacer(Modifier.size(24.dp))

            SettingsSwitchRow(
                title = stringResource(R.string.settings_live_update_enabled),
                summary = stringResource(R.string.settings_live_update_enabled_summary),
                checked = state.liveUpdateEnabled,
                onCheckedChange = onLiveUpdateChange,
            )

            // Only say something when there is something to fix. A row that always reads
            // "granted" trains the reader to stop reading it.
            when {
                !notificationsEnabled -> SettingsStatusRow(
                    title = stringResource(R.string.settings_live_update_permission),
                    status = stringResource(R.string.settings_permission_blocked),
                    action = {
                        TextButton(onClick = onOpenNotificationSettings) {
                            Text(stringResource(R.string.settings_open_settings))
                        }
                    },
                )

                !canPromote -> SettingsStatusRow(
                    title = stringResource(R.string.settings_notification_support),
                )
            }

            if (!liveUpdatesSupported) {
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.settings_notification_support),
                    style = MaterialTheme.pairTypography.supporting,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.size(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.size(24.dp))

            SectionHeader(
                icon = Icons.Outlined.LocationOn,
                text = stringResource(R.string.settings_location),
            )
            Spacer(Modifier.size(24.dp))

            if (locationStatus != stringResource(R.string.settings_permission_granted)) {
                SettingsStatusRow(
                    title = stringResource(R.string.settings_location_permission),
                    status = locationStatus,
                    action = {
                        TextButton(onClick = onOpenLocationSettings) {
                            Text(stringResource(R.string.settings_open_settings))
                        }
                    },
                )
            }

            SettingsStatusRow(
                title = stringResource(R.string.battery_optimization),
                status = if (batteryUnrestricted) {
                    stringResource(R.string.battery_allowed)
                } else {
                    stringResource(R.string.battery_restricted)
                },
                action = if (batteryUnrestricted) {
                    null
                } else {
                    {
                        TextButton(onClick = onOpenBatterySettings) {
                            Text(stringResource(R.string.settings_open_settings))
                        }
                    }
                },
            )

            Spacer(Modifier.size(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.size(24.dp))

            if (state.currentRoomId == null) {
                SettingsStatusRow(
                    title = stringResource(R.string.settings_no_room),
                )
            } else {
                SettingsStatusRow(
                    title = stringResource(R.string.settings_leave_room),
                    status = null,
                    trailing = {
                        // Error-coloured, because this is the one row on the screen that destroys
                        // something the user cannot get back.
                        SmallFloatingActionButton(
                            onClick = onLeaveRoom,
                            modifier = Modifier.size(56.dp),
                            shape = MaterialTheme.pairShapes.iconButton,
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = stringResource(R.string.settings_leave_room),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    },
                )
            }

            PeerDevices.controller?.let { peer ->
                Spacer(Modifier.size(16.dp))
                // No divider here: `PeerSection` draws its own. Adding a second one put two rules
                // on top of each other directly under the "Leave room" row.
                PeerSection(peer = peer, currentRoomId = state.currentRoomId)
            }
        }

        // The bottom bar is present but nothing on it is selected: Settings is not one of the
        // three destinations, and pretending otherwise would leave a misleading highlight.
        PairBottomBar(
            selected = null,
            onSelect = rememberDestinationRouter(
                currentRoomId = state.currentRoomId,
                onOpenHome = onNavigateHome,
                onOpenRoom = onOpenRoom,
                onOpenLocation = onOpenLocationRules,
                snackbarHostState = snackbarHostState,
            ),
            onSettings = {},
            showPair = state.currentRoomId != null,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 104.dp)
                .padding(horizontal = 20.dp),
        ) { data ->
            Snackbar(
                snackbarData = data,
                shape = MaterialTheme.pairShapes.cardSmall,
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                actionColor = MaterialTheme.colorScheme.inversePrimary,
            )
        }
    }
}

/**
 * The display-name field, edited in place.
 *
 * The comp edits the name where it is shown rather than in a dialog, which is both fewer taps
 * and one less surface between the person and their own name. The pencil action toggles the
 * field into an editable state; committing happens on the keyboard's Done action.
 */
@Composable
private fun NameField(
    displayName: String,
    isEditing: Boolean,
    draft: String,
    onStartEditing: () -> Unit,
    onDraftChange: (String) -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit,
) {
    val motion = MaterialTheme.pairMotion
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(isEditing, focusRequester) {
        if (isEditing) focusRequester.requestFocus()
    }
    LaunchedEffect(isEditing) {
        if (!isEditing) keyboard?.hide()
    }

    Column {
        if (isEditing) {
            // The same field the rule editor's "Place name" uses, so the two are one component
            // rather than two that have to be kept in step. See `PairNameField`.
            PairNameField(
                value = draft,
                onValueChange = onDraftChange,
                placeholder = stringResource(R.string.settings_edit_name),
                focusRequester = focusRequester,
                onImeAction = {
                    keyboard?.hide()
                    onCommit()
                },
                trailingContent = {
                    IconButton(
                        onClick = {
                            keyboard?.hide()
                            onCancel()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.rules_cancel),
                        )
                    }
                },
            )
        } else {
            val nameInteraction = remember { MutableInteractionSource() }
            Surface(
                onClick = onStartEditing,
                interactionSource = nameInteraction,
                shape = MaterialTheme.pairShapes.field,
                color = PairSurfaces.card,
                contentColor = PairSurfaces.onCard,
                // The barest press. This is a full-width row, so a visible shrink would move the
                // name itself; the row's job here is to be obviously tappable, not to squash.
                modifier = Modifier
                    .rememberPressScale(nameInteraction, pressedScale = 0.99f)
                    .fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = displayName.ifBlank { stringResource(R.string.onboarding_name_hint) },
                        style = MaterialTheme.pairTypography.screenTitle,
                        color = PairSurfaces.onCard,
                        modifier = Modifier.weight(1f),
                    )
                    // An affordance, not a second target. This used to be its own clickable
                    // Surface nested inside the row's clickable Surface: two targets for one
                    // action, with the inner one eating taps in its 44dp square. The whole row is
                    // the control, and this says so.
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }

        // The Save action, revealed while the name is being edited.
        //
        // A button-sized element, so `fast` on both axes. It used to pass no spec at all, which
        // quietly fell back to Compose's default spring — outside the scheme, and therefore
        // outside the reduced-motion preference as well.
        AnimatedVisibility(
            visible = isEditing,
            enter = fadeIn(animationSpec = motion.fastEffectsSpec()) +
                expandVertically(animationSpec = motion.fastSpatialSpec()),
            exit = fadeOut(animationSpec = motion.fastEffectsSpec()) +
                shrinkVertically(animationSpec = motion.fastSpatialSpec()),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    onClick = {
                        keyboard?.hide()
                        onCommit()
                    },
                    shape = MaterialTheme.pairShapes.button,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.settings_save_name),
                        style = MaterialTheme.pairTypography.controlLabel,
                    )
                }
            }
        }
    }
}

/** A titled row with a summary and a switch. */
@Composable
private fun SettingsSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier.padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Top,
        ) {
            Text(
                text = title,
                style = MaterialTheme.pairTypography.itemTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = summary,
                style = MaterialTheme.pairTypography.supporting,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

/**
 * A row that reports a state, and optionally offers the one action that can change it.
 *
 * [action] and [trailing] are alternatives rather than two slots to fill: a row either explains
 * itself with a link to the system setting, or ends in a control. Having both would put a button
 * and a switch on one line, which is not a shape this design uses.
 */
@Composable
private fun SettingsStatusRow(
    title: String,
    status: String? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Top,
        ) {
            Text(
                text = title,
                style = MaterialTheme.pairTypography.itemTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (status != null) {
                Text(
                    text = status,
                    style = MaterialTheme.pairTypography.supporting,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        } else if (action != null) {
            action()
        }
    }
}

/**
 * Light / dark / system, as one continuous track with a filled selection.
 *
 * A segmented control rather than a switch because there are three states, and a switch cannot
 * say which of them it is in.
 */
@Composable
private fun ThemeModeSelector(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    val modes = listOf(
        ThemeMode.System to R.string.settings_theme_system,
        ThemeMode.Dark to R.string.settings_theme_dark,
        ThemeMode.Light to R.string.settings_theme_light,
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, (mode, labelRes) ->
            SegmentedButton(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                // No tick: the selection is already shown by the fill, and the design's segments
                // carry a label and nothing else. A tick is a second signal for the same fact.
                icon = {},
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primary,
                    activeContentColor = MaterialTheme.colorScheme.onPrimary,
                    activeBorderColor = Color.Transparent,
                    // The control reads as one track with a filled selection in it, so the
                    // unselected segments show the page through rather than a card — a card here
                    // would draw a seam down the middle of a shape the design has undivided.
                    inactiveContainerColor = Color.Transparent,
                    inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    inactiveBorderColor = Color.Transparent,
                ),
            ) {
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.pairTypography.controlLabel,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Reads the platform's permission and battery state, and keeps it current.
 *
 * Both can be changed from system settings while Pair is in the background, so the read happens
 * on every resume rather than once at composition: a value captured at first composition is
 * correct only until the user leaves the app, which is exactly when they are most likely to
 * have changed it.
 */
@Composable
private fun rememberPermissionAndBatteryState(): PermissionAndBatteryState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state = remember { mutableStateOf(readPermissionAndBattery(context)) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                state.value = readPermissionAndBattery(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return state.value
}

private fun readPermissionAndBattery(context: Context): PermissionAndBatteryState {
    val hasLocation = ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val power = context.getSystemService(PowerManager::class.java)
    val unrestricted = power?.isIgnoringBatteryOptimizations(context.packageName) == true
    return PermissionAndBatteryState(hasLocation, unrestricted)
}

/**
 * Whether the Material You switch can be offered at all.
 *
 * Below Android 12 there is no dynamic colour to follow, so the row would be a switch that does
 * nothing. The row is hidden rather than shown disabled: a permanently-off control is a
 * question the screen cannot answer.
 */
private fun supportsDynamicColorSetting(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Opens this app's notification settings.
 *
 * Wrapped in a runCatching because a device can lack the activity — some OEM builds do — and a
 * Settings row that crashes the screen is worse than one that does nothing.
 */
private fun Context.openNotificationSettings() {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    runCatching { startActivity(intent) }
}

private fun Context.openBatterySettings() {
    runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
}

private fun Context.openLocationSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", packageName, null))
    runCatching { startActivity(intent) }
}
