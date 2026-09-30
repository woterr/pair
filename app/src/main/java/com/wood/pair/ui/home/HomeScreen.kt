package com.wood.pair.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wood.pair.R
import com.wood.pair.data.PairGraph
import com.wood.pair.ui.components.Avatars
import com.wood.pair.ui.components.DecorativeBlob
import com.wood.pair.ui.components.DecorativeStarburst
import com.wood.pair.ui.components.PairDestination
import com.wood.pair.ui.components.PairScaffold
import com.wood.pair.ui.components.PairTopBar
import com.wood.pair.ui.components.PairWordmark
import com.wood.pair.ui.components.RoomIdText
import com.wood.pair.ui.motion.rememberPressScale
import com.wood.pair.ui.rememberPairViewModel
import com.wood.pair.ui.theme.LocalMotionScheme
import com.wood.pair.ui.theme.PairSurfaces
import com.wood.pair.ui.theme.decorTint
import com.wood.pair.ui.theme.pairMotion
import com.wood.pair.ui.theme.pairShapes
import com.wood.pair.ui.theme.pairTypography
import androidx.compose.material3.SnackbarHostState
import com.wood.pair.ui.components.rememberDestinationRouter

/**
 * The page margin, measured off the comps.
 *
 * Every screen's cards, fields and rules sit on the same line, so the value lives here rather than
 * being repeated — a margin that differs by a dp between two screens is exactly the kind of thing
 * that reads as "unfinished" without anyone being able to say why.
 */
private val ScreenMargin = 20.dp

/** Height of the two side-by-side actions, from the comp. */
private val ActionHeight = 68.dp

@Composable
fun HomeScreen(
    graph: PairGraph,
    onOpenRoom: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLocation: () -> Unit,
) {
    val viewModel = rememberPairViewModel(graph) { g ->
        HomeViewModel(
            preferences = g.preferences,
            auth = g.authRepository,
            roomRepository = g.roomRepository,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    HomeContent(
        state = state,
        avatarResId = Avatars.resIdOf(state.avatarId),
        onCreateRoom = { viewModel.createRoom(onOpenRoom) },
        onShowJoin = viewModel::showJoinSheet,
        onDismissJoin = viewModel::dismissJoinSheet,
        onJoinInputChange = viewModel::onJoinInputChange,
        onJoin = { viewModel.joinRoom(onOpenRoom) },
        onOpenRoom = onOpenRoom,
        onOpenSettings = onOpenSettings,
        onOpenLocation = onOpenLocation,
        onDismissError = viewModel::dismissError,
    )
}

/**
 * The home screen's content, from plain state.
 *
 * Separated from [HomeScreen] so it can be rendered without Firebase, a view model or a signed-in
 * account — which is what the screenshot and preview harnesses need. The stateful wrapper above
 * does nothing but supply this composable's arguments.
 */
@Composable
internal fun HomeContent(
    state: HomeUiState,
    avatarResId: Int?,
    onCreateRoom: () -> Unit,
    onShowJoin: () -> Unit,
    onDismissJoin: () -> Unit,
    onJoinInputChange: (String) -> Unit,
    onJoin: () -> Unit,
    onOpenRoom: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLocation: () -> Unit,
    onDismissError: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    PairScaffold(
        // No decor. The soft circle behind the wordmark and the starburst in the top-right are
        // both gone: they were positioned with hardcoded offsets against the window rather than
        // against any layout, so they drifted with the status bar, collided with the avatar on a
        // shorter device, and cut through the "Continue to room" card at some insets. The
        // wordmark and the cards carry the screen on their own.
        topBar = {
            PairTopBar(displayName = state.displayName, avatarResId = avatarResId)
        },
        selectedDestination = PairDestination.Home,
        hasRoom = state.currentRoomId != null,
        onSelectDestination = rememberDestinationRouter(
            currentRoomId = state.currentRoomId,
            onOpenHome = {},
            onOpenRoom = onOpenRoom,
            onOpenLocation = onOpenLocation,
            snackbarHostState = snackbarHostState,
        ),
        onOpenSettings = onOpenSettings,
        snackbarHostState = snackbarHostState,
    ) { bottomPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = ScreenMargin)
                .padding(bottom = bottomPadding),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Measured off the comp: the wordmark's ink begins 100.6dp from the top of the
                // frame, and the top bar occupies 56dp of that.
                Spacer(Modifier.height(44.dp))

                // The lockup, centred as the comp has it.
                PairWordmark(
                    partnerName = null,
                    align = Alignment.Start,
                )

                // The comp puts 61dp between the bottom of "Partner" and the top of the card.
                Spacer(Modifier.height(16.dp))

                // Only present when there is a room to return to, and it fades rather than
                // appearing: the whole column would otherwise jump when a room is created.
                AnimatedVisibility(
                    visible = state.hasRoom,
                    enter = fadeIn(MaterialTheme.pairMotion.defaultEffectsSpec()),
                    exit = fadeOut(MaterialTheme.pairMotion.defaultEffectsSpec()),
                ) {
                    state.currentRoomId?.let { roomId ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Spacer(Modifier.height(16.dp))
                            ContinueToRoomCard(
                                roomId = roomId,
                                onClick = { onOpenRoom(roomId) },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Left-aligned against the card above it, as the comp has it: the label reads as
                // a continuation of the card's edge rather than as a centred divider.
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.or_label),
                        style = MaterialTheme.pairTypography.supporting,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val joinInteraction = remember { MutableInteractionSource() }
                    FilledTonalButton(
                        onClick = onShowJoin,
                        // Not equal weights: "Create a room" is the action the screen exists to
                        // offer, and the comp gives it the wider half.
                        modifier = Modifier
                            .weight(0.95f)
                            .height(ActionHeight)
                            .rememberPressScale(joinInteraction),
                        enabled = !state.isBusy,
                        shape = MaterialTheme.pairShapes.button,
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        interactionSource = joinInteraction,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Login,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.join_a_room),
                            style = MaterialTheme.pairTypography.controlLabel,
                            maxLines = 1,
                        )
                    }

                    val createInteraction = remember { MutableInteractionSource() }
                    Button(
                        onClick = onCreateRoom,
                        modifier = Modifier
                            .weight(1.05f)
                            .height(ActionHeight)
                            .rememberPressScale(createInteraction),
                        enabled = !state.isBusy,
                        shape = MaterialTheme.pairShapes.button,
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        interactionSource = createInteraction,
                    ) {
                        // The label turns into a spinner in place rather than being replaced by
                        // one beside it, so the button never changes width mid-press.
                        if (state.isCreating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.home_create_room_long),
                                style = MaterialTheme.pairTypography.controlLabel,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }

    // The two are mutually exclusive: an error raised while the sheet is open belongs in the
    // sheet, where the input that caused it is still on screen. The message is resolved here
    // rather than inside the dialog's content lambda, where the null check would not carry.
    val dialogMessage = state.error
        ?.takeIf { !state.joinSheetVisible }
        ?.asMessage()

    if (state.joinSheetVisible) {
        JoinRoomDialog(
            input = state.joinInput,
            isJoining = state.isJoining,
            error = state.error,
            onInputChange = onJoinInputChange,
            onConfirm = onJoin,
            onDismiss = onDismissJoin,
        )
    }

    if (dialogMessage != null) {
        AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text(stringResource(R.string.error_generic)) },
            text = { Text(dialogMessage) },
            confirmButton = {
                TextButton(onClick = onDismissError) {
                    Text(stringResource(R.string.error_dismiss))
                }
            },
        )
    }
}

/**
 * The way back into a room that already exists.
 *
 * Material 3 Expressive styling with generous vertical padding and clean layout.
 */
@Composable
private fun ContinueToRoomCard(
    roomId: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val motion = LocalMotionScheme.current

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .rememberPressScale(interactionSource, pressedScale = 0.98f),
        shape = MaterialTheme.pairShapes.card,
        color = PairSurfaces.card,
        contentColor = PairSurfaces.onCard,
    ) {
        Row(
            modifier = Modifier
                .padding(start = 24.dp, end = 16.dp, top = 18.dp, bottom = 18.dp)
                .animateContentSize(animationSpec = motion.defaultSpatialSpec()),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_continue_to_room),
                    style = MaterialTheme.pairTypography.supporting,
                    color = PairSurfaces.onCardMuted,
                )
                Spacer(Modifier.height(4.dp))
                RoomIdText(
                    roomId = roomId,
                    color = PairSurfaces.onCard,
                )
            }

            Surface(
                modifier = Modifier.size(44.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        }
    }
}

/**
 * The join-room dialog.
 *
 * A dialog rather than a screen because joining is a single field: a screen would imply there is
 * more to it, and there is not. The confirm button is disabled until the code is non-blank and
 * stops accepting input while the request is in flight, so a slow network cannot produce two
 * joins.
 */
@Composable
private fun JoinRoomDialog(
    input: String,
    isJoining: Boolean,
    error: HomeError?,
    onInputChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        // Dismissing mid-request would leave the user in a room they had not seen yet.
        onDismissRequest = { if (!isJoining) onDismiss() },
        title = { Text(stringResource(R.string.join_title)) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.join_room_id_label)) },
                placeholder = { Text(stringResource(R.string.join_room_id_hint)) },
                singleLine = true,
                isError = error != null,
                supportingText = {
                    // Resolved before the visibility check: a null-check does not carry into the
                    // content lambda, and this way the row only exists when it has something
                    // to say.
                    val message = error?.asMessage()
                    AnimatedVisibility(visible = message != null) {
                        Text(message.orEmpty())
                    }
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(
                    onGo = { if (!isJoining) onConfirm() },
                ),
                shape = MaterialTheme.pairShapes.field,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isJoining && input.isNotBlank(),
                shape = MaterialTheme.pairShapes.button,
            ) {
                if (isJoining) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.join_confirm))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isJoining,
            ) {
                Text(stringResource(R.string.join_cancel))
            }
        },
    )
}

/**
 * The user-facing text for a join or create failure.
 *
 * Written as a total function rather than one with a `null` branch: every case the error type can
 * be in has something to say, so there is no path where a failure is reported as silence.
 */
@Composable
private fun HomeError.asMessage(): String = when (this) {
    HomeError.RoomNotFound -> stringResource(R.string.error_room_not_found)
    HomeError.RoomFull -> stringResource(R.string.error_room_full)
    HomeError.InvalidRoomId -> stringResource(R.string.error_invalid_room_id)
    HomeError.Offline -> stringResource(R.string.error_offline)
    HomeError.TimedOut -> stringResource(R.string.error_timed_out)
    HomeError.CreateFailed -> stringResource(R.string.error_create_failed)
    HomeError.JoinFailed -> stringResource(R.string.error_join_failed)
    HomeError.SessionFailed -> stringResource(R.string.error_auth_failed)
}
