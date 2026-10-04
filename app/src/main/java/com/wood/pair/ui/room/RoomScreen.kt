package com.wood.pair.ui.room

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.content.ClipData
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wood.pair.R
import com.wood.pair.data.PairGraph
import com.wood.pair.notifications.ChipEmphasisTransformation
import com.wood.pair.ui.components.Avatars
import com.wood.pair.ui.components.PairDestination
import com.wood.pair.ui.components.PairScaffold
import com.wood.pair.ui.components.PairTopBar
import com.wood.pair.ui.rememberPairViewModel
import kotlinx.coroutines.launch
import com.wood.pair.ui.shape.IndeterminateCircular
import com.wood.pair.ui.shape.IndeterminateWave
import com.wood.pair.ui.theme.MotionScheme
import com.wood.pair.ui.theme.PairSurfaces
import com.wood.pair.ui.theme.Space
import com.wood.pair.ui.theme.pairMotion
import com.wood.pair.ui.theme.pairShapes
import com.wood.pair.ui.theme.pairTypography
import androidx.compose.material3.SnackbarHostState
import com.wood.pair.ui.components.rememberDestinationRouter
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.wood.pair.ui.motion.rememberPressScale
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/**
 * The one canned status, offered as a suggestion rather than bound to a button.
 *
 * It used to be what "Set status" *did*, which meant the button could only ever set this one
 * phrase and threw away whatever had been typed — a button whose label said "set" and whose
 * effect was "overwrite". It is now a suggestion inside the card, so the shortcut survives
 * without stealing the button's meaning.
 */
private const val STATUS_PROMPT = "Going to…"

@Composable
fun RoomScreen(
    graph: PairGraph,
    roomId: String,
    onNavigateBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHome: () -> Unit,
    onOpenLocation: () -> Unit,
) {
    val application = LocalContext.current.applicationContext as android.app.Application
    val viewModel = rememberPairViewModel(graph, key = roomId) { g ->
        RoomViewModel(
            application = application,
            roomId = roomId,
            preferences = g.preferences,
            auth = g.authRepository,
            roomRepository = g.roomRepository,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current

    // Copying is a one-shot side effect and the platform clipboard is written through a
    // suspending call, so the action launches rather than calling inline.
    val scope = rememberCoroutineScope()
    val onCopyRoomId: () -> Unit = {
        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, roomId))) }
    }

    // One-shot navigation: the view model asks to be popped, the screen does it, and the request
    // is not repeated on recomposition. A room that has gone away is a different case from one
    // that was left deliberately, so it is handled rather than ignored.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is RoomEvent.LeftRoom -> onNavigateBack()
                RoomEvent.RoomGone -> onNavigateBack()
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    RoomContent(
        state = state,
        roomId = roomId,
        onCopyRoomId = onCopyRoomId,
        onLiveTextChange = viewModel::onLiveTextChange,
        onEditingChanged = viewModel::onEditingChanged,
        onClear = viewModel::clearLiveText,
        onSetStatus = viewModel::commitLiveText,
        onSelectDestination = rememberDestinationRouter(
            currentRoomId = state.roomId.takeIf { it.isNotEmpty() },
            onOpenHome = onOpenHome,
            onOpenRoom = {},
            onOpenLocation = onOpenLocation,
            snackbarHostState = snackbarHostState,
        ),
        onOpenSettings = onOpenSettings,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * The room screen, from plain state.
 *
 * The whole screen, chrome included, not just its body: a render of this is the screen a user
 * sees, which is the only thing worth comparing against a comp. It exists separately from
 * [RoomScreen] so that rendering needs no Firebase, no view model and no live room.
 */
@Composable
internal fun RoomContent(
    state: RoomUiState,
    roomId: String,
    onCopyRoomId: () -> Unit,
    onLiveTextChange: (String) -> Unit,
    onEditingChanged: (Boolean) -> Unit,
    onClear: () -> Unit,
    onSetStatus: () -> Unit,
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
        selectedDestination = PairDestination.Pair,
        hasRoom = true,
        onSelectDestination = onSelectDestination,
        onOpenSettings = onOpenSettings,
        snackbarHostState = snackbarHostState,
    ) { bottomPadding ->
        RoomBody(
            state = state,
            roomId = roomId,
            onCopyRoomId = onCopyRoomId,
            onLiveTextChange = onLiveTextChange,
            onEditingChanged = onEditingChanged,
            onClear = onClear,
            onSetStatus = onSetStatus,
            modifier = Modifier.padding(bottom = bottomPadding),
        )
    }
}

/** The room screen's scrolling body, inside the shared chrome. */
@Composable
private fun RoomBody(
    state: RoomUiState,
    roomId: String,
    onCopyRoomId: () -> Unit,
    onLiveTextChange: (String) -> Unit,
    onEditingChanged: (Boolean) -> Unit,
    onClear: () -> Unit,
    onSetStatus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.pageGutter)
            .padding(top = 16.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        PartnerLine(
            state = state,
            roomId = roomId,
            onCopyRoomId = onCopyRoomId,
        )

        Spacer(Modifier.height(Space.blockGap))

        // The partner's status is what this screen is for, so it sits above the editor
        // rather than below it: the editor is a reply, the status is the message.
        PartnerStatusCard(state = state)

        Spacer(Modifier.height(Space.blockGap))

        YourStatusLabel(state = state)

        Spacer(Modifier.height(Space.labelGap))

        LiveTextField(
            state = state,
            onValueChange = onLiveTextChange,
            onEditingChanged = onEditingChanged,
        )

        Spacer(Modifier.height(Space.tightGap))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            FilledTonalButton(
                onClick = onClear,
                modifier = Modifier.height(56.dp),
                // Nothing to clear means nothing to press: an always-live button here would
                // silently do nothing, which reads as the app being broken.
                enabled = state.draft.isNotEmpty(),
                shape = MaterialTheme.pairShapes.button,
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.room_clear),
                    style = MaterialTheme.pairTypography.controlLabel,
                    maxLines = 1,
                )
            }

            Button(
                onClick = onSetStatus,
                // Live only when there is something to send: a non-empty draft that differs from
                // what is already published. See `RoomUiState.canCommit`. Pressing it drives the
                // two together, so the button disabling itself afterwards is also the
                // confirmation that the status went out.
                enabled = state.canCommit,
                modifier = Modifier.height(56.dp),
                shape = MaterialTheme.pairShapes.button,
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.set_status),
                    style = MaterialTheme.pairTypography.controlLabel,
                    maxLines = 1,
                )
            }
        }

        Spacer(Modifier.height(Space.blockGap))

        // In the column's flow, right-aligned, rather than floating in the bottom corner of the
        // screen. It used to be `align(Alignment.BottomEnd)` on a Box wrapping the column, which
        // put it against the *window* edge while every card on the screen sat 20dp inside it, so
        // the button's right edge sat 20dp proud of the card above it and read as misaligned.
        // In the flow it inherits the same gutter as the cards by construction, and it sits
        // directly under the buttons it explains rather than at an arbitrary distance from them.
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            SoloStatusExplainer(visible = !state.isPartnerPresent)
        }

        Spacer(Modifier.height(Space.blockGap))

        // The "sent" confirmation. A chip that rises from under the buttons, not a dialog and not
        // a snackbar across the bottom of the screen: the acknowledgement belongs next to the
        // button that earned it, and it must not steal focus from a field the user is still in.
        SentStatusChip(text = state.sentText)
    }
}

/**
 * A small chip that rises into place when a status is published, and sinks away again on its own.
 *
 * ## Why it rises rather than fading
 *
 * Material's own rule is that a spatial spring drives movement and an effects spring drives
 * opacity, and the two must not be mixed. A chip arriving is movement — it has a position to get
 * to — so it gets `fastSpatialSpec`, and its fade is the paired `fastEffectsSpec`. The chip is a
 * small element and the message is transient, which is the `fast` speed on both axes.
 *
 * ## Why it is here and not a snackbar
 *
 * A snackbar slides in from the screen edge and takes the bottom of the display for several
 * seconds. That is the right weight for "you are offline" and the wrong weight for "that worked",
 * which is the outcome the user just asked for and already expects. This chip says it in a line,
 * beside the button, and gets out of the way.
 */
@Composable
private fun SentStatusChip(text: String?) {
    val motion = MaterialTheme.pairMotion

    AnimatedVisibility(
        visible = text != null,
        enter = expandVertically(
            animationSpec = motion.fastSpatialSpec(),
            expandFrom = Alignment.Bottom,
        ) + fadeIn(animationSpec = motion.fastEffectsSpec()),
        exit = shrinkVertically(
            animationSpec = motion.fastSpatialSpec(),
            shrinkTowards = Alignment.Bottom,
        ) + fadeOut(animationSpec = motion.fastEffectsSpec()),
    ) {
        Surface(
            shape = MaterialTheme.pairShapes.pill,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Space.panelPadding, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.room_status_sent, text.orEmpty()),
                    style = MaterialTheme.pairTypography.controlLabel,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The "nobody else is here yet" explainer: a small affordance in the corner that opens a dialog.
 *
 * While you are alone, what you write is what *you* see, because there is no one to send it to.
 * That is a genuine surprise the first time it happens, and the rule is not guessable, so the app
 * offers to say it rather than leaving you to work out why your own words appeared in the status
 * bar.
 *
 * A button rather than a card permanently pinned over the content. A permanent card would sit on
 * top of the "Set status" row and eat taps meant for it, and a hint that occludes the thing it is
 * hinting about is worse than no hint. The dialog also gets out of the way on its own, which
 * means the explanation is asked for once rather than read on every visit.
 *
 * Appears and disappears with the room's own state, not on a timer: it is true exactly while
 * `memberUid` is null, and a hint that can be wrong is worse than no hint.
 */
@Composable
private fun SoloStatusExplainer(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.pairMotion
    var shown by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = visible && !shown,
        modifier = modifier,
        enter = scaleIn(
            animationSpec = motion.fastSpatialSpec(),
            initialScale = 0.4f,
        ) + fadeIn(animationSpec = motion.fastEffectsSpec()),
        exit = scaleOut(
            animationSpec = motion.fastSpatialSpec(),
            targetScale = 0.4f,
        ) + fadeOut(animationSpec = motion.fastEffectsSpec()),
    ) {
        FilledTonalIconButton(
            onClick = { shown = true },
            shape = MaterialTheme.pairShapes.pill,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = PairSurfaces.card,
                contentColor = PairSurfaces.onCard,
            ),
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = stringResource(R.string.room_solo_explain_cd),
                modifier = Modifier.size(22.dp),
            )
        }
    }

    if (shown) {
        AlertDialog(
            onDismissRequest = { shown = false },
            title = {
                Text(
                    text = stringResource(R.string.room_solo_explainer_title),
                    style = MaterialTheme.pairTypography.cardTitle,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.room_solo_explainer),
                    style = MaterialTheme.pairTypography.body,
                )
            },
            confirmButton = {
                TextButton(onClick = { shown = false }) {
                    Text(
                        text = stringResource(R.string.action_done),
                        style = MaterialTheme.pairTypography.controlLabel,
                    )
                }
            },
            shape = MaterialTheme.pairShapes.card,
        )
    }
}

/**
 * The screen's one line of orientation: "You are paired with Snoopy", or "Waiting for your
 * partner…", with the room code on the same line beside it.
 *
 * ## Why one line and not two
 *
 * This has been three arrangements at different points, and the reason it keeps moving is worth
 * recording. The code chip started on the same row, which in the wide italic serif left so little
 * room that "You are paired with" took the first line and the partner's **name** was stranded
 * alone on the second — reading as a caption under a heading, the opposite of one statement. So
 * the chip moved below. That was right for the serif and wrong for this one: with the sentence in
 * Google Sans Flex at 18sp and the name in Black, both fit on one line beside a compact chip, and
 * a single line is what a status line should be.
 *
 * The two are on one line because they answer one question — *which room, with whom* — and stacked
 * they read as two unrelated facts. The row is centred, so the chip sits on the sentence's axis
 * rather than floating above it.
 *
 * The wave that used to underline the name is gone: it was a third visual event competing with a
 * sentence that is meant to be plain.
 */
@Composable
private fun PartnerLine(
    state: RoomUiState,
    roomId: String,
    onCopyRoomId: () -> Unit,
) {
    // Null when there is nobody to name, which is the only case where the sentence differs.
    val partnerName = if (state.isPartnerPresent) {
        state.partnerName ?: stringResource(R.string.room_partner_unknown)
    } else {
        null
    }

    // Resolved before the annotated string is built, because `buildAnnotatedString` is not
    // composable and `stringResource` is. Reading them first keeps the resource lookup in a
    // composable scope where it belongs.
    val pairedWith = stringResource(R.string.room_paired_with)
    val waiting = stringResource(R.string.room_waiting_for_partner)
    val prefixStyle = MaterialTheme.pairTypography.pairedWith.toSpan()
    val nameStyle = MaterialTheme.pairTypography.pairedWithName.toSpan()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        // Centre, not Top. The chip is a pill and the sentence is a line of text; aligning their
        // boxes to the top leaves the chip's cap height sitting above the text's, which reads as
        // a misaligned row. Centring puts the chip on the sentence's own axis.
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Two runs, one line, one sentence: the fixed clause in Medium and the name in Black.
        // They have to be one `Text` rather than two `Text`s side by side, because only a single
        // text can be given a weighted max width and ellipsised as a unit - and it has to be, or
        // a long partner name wraps under the sentence and breaks the row.
        Text(
            text = if (partnerName != null) {
                buildAnnotatedString {
                    withStyle(prefixStyle) { append(pairedWith) }
                    append(" ")
                    withStyle(nameStyle) { append(partnerName) }
                }
            } else {
                buildAnnotatedString {
                    withStyle(prefixStyle) { append(waiting) }
                }
            },
            color = if (partnerName != null) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = Space.labelGap)
                .semantics { heading() },
        )

        RoomCodeChip(
            roomId = roomId,
            onCopy = onCopyRoomId,
            label = stringResource(R.string.cd_copy_room_id),
        )
    }
}

/**
 * The partner's status: a name, a big heading, and the message they are broadcasting.
 *
 * This is the mirror of the editor below it, and it is the surface the Live Update is built
 * from — whatever is written in this card is exactly what appears in the status bar. The
 * correspondence is deliberate and one-to-one so the notification is never a surprise.
 *
 * While no partner is present the card is not drawn at all. Rendering an empty "Partner's status"
 * heading would state a fact about the room that is false, and the space is better spent letting
 * the editor sit higher while you are still waiting.
 */
@Composable
private fun PartnerStatusCard(state: RoomUiState) {
    val motion = MaterialTheme.pairMotion
    val name = state.partnerName ?: stringResource(R.string.room_partner_unknown)
    val status = state.partnerText

    // Animating the card in means the room's main event — someone arriving — is a movement rather
    // than a repaint, and the card does not simply appear in a place the eye was already resting.
    AnimatedVisibility(
        visible = state.isPartnerPresent,
        enter = expandVertically(
            animationSpec = motion.defaultSpatialSpec(),
        ) + fadeIn(animationSpec = motion.defaultEffectsSpec()),
        exit = shrinkVertically(
            animationSpec = motion.defaultSpatialSpec(),
        ) + fadeOut(animationSpec = motion.defaultEffectsSpec()),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.pairShapes.card,
            color = PairSurfaces.card,
            contentColor = PairSurfaces.onCard,
        ) {
            Column(modifier = Modifier.padding(Space.cardPadding)) {
                Text(
                    text = stringResource(R.string.room_partner_status, name),
                    style = MaterialTheme.pairTypography.statusHeading,
                    color = PairSurfaces.onCard,
                )

                Spacer(Modifier.height(10.dp))

                if (status.isBlank()) {
                    // An empty partner card gets a resting state, not a blank. A large card with
                    // nothing in it reads as a loading failure; this reads as "they have not said
                    // anything", which is what it means.
                    Text(
                        text = stringResource(R.string.room_partner_nothing_yet),
                        style = MaterialTheme.pairTypography.supporting,
                        color = PairSurfaces.onCardMuted,
                    )
                } else {
                    Text(
                        text = status,
                        style = MaterialTheme.pairTypography.partnerMessage,
                        color = PairSurfaces.onCard,
                    )
                }
            }
        }
    }
}

/** The label over the editor, so the two stages on this screen are never confused. */
@Composable
private fun YourStatusLabel(state: RoomUiState) {
    Text(
        text = if (state.isPartnerPresent) {
            stringResource(R.string.room_your_status_to, state.partnerName ?: "")
        } else {
            stringResource(R.string.room_your_status_alone)
        },
        style = MaterialTheme.pairTypography.sectionLabel,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/**
 * A [TextStyle] reduced to just the fields a [SpanStyle] can carry inside an
 * [androidx.compose.ui.text.AnnotatedString].
 *
 * The alternative is `TextStyle.toSpanStyle()`, but this file declares its own `StyleSpan` and the
 * two resolve to the same name. Naming the conversion here keeps the call site honest about what
 * it is doing: a run's typeface, weight and size, and nothing else. Line height is deliberately
 * dropped — it is a paragraph property, and setting it per run is what makes mixed-weight lines
 * sit unevenly.
 */
private fun TextStyle.toSpan(): SpanStyle = SpanStyle(
    fontFamily = fontFamily,
    fontWeight = fontWeight,
    fontSize = fontSize,
    letterSpacing = letterSpacing,
)
/** The room code, tappable to copy — the code is read aloud, so copying it is a real action. */
@Composable
private fun RoomCodeChip(
    roomId: String,
    onCopy: () -> Unit,
    label: String,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        onClick = onCopy,
        interactionSource = interactionSource,
        modifier = Modifier
            .rememberPressScale(interactionSource)
            .semantics { contentDescription = label },
        shape = MaterialTheme.pairShapes.pill,
        color = PairSurfaces.card,
        contentColor = PairSurfaces.onCard,
    ) {
        Row(
            // 12dp of horizontal padding, not more. The chip has to be comfortably tappable —
            // 44dp tall is what that costs vertically — but every extra dp here is taken from
            // the partner's name on the same line, and a name that ellipsises because the room
            // code beside it is padded generously is the wrong trade.
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Link,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = roomId,
                style = MaterialTheme.pairTypography.roomIdCompact,
                maxLines = 1,
            )
        }
    }
}

/**
 * What the room shows when there is no live text.
 *
 * The ring is the visible counterpart of the Live Update that is deliberately *not* being
 * posted: the same fact, shown in the app, so a user who has cleared their text can see that
 * the pause is working rather than guessing whether it failed. The two bars in the middle say
 * the same thing in one glance and, more usefully, distinguish "paused" from "working" — a
 * spinning ring on its own means both, and this is the one state where nothing is happening.
 */
@Composable
private fun PausedState(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.cd_live_text_empty)
    Box(
        modifier = modifier.semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        IndeterminateCircular(
            color = MaterialTheme.colorScheme.primary,
            // 180dp of box for the comp's 148dp of visible ring; the indicator artwork carries
            // its own inset, so the box has to be larger than the mark it draws.
            modifier = Modifier.size(180.dp),
        )
        PauseGlyph(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(34.dp),
        )
    }
}

/**
 * The pause mark: two rounded bars, the universal shape for "held".
 *
 * Drawn here rather than pulled from the Material icon set because the comp's mark is a
 * specific weight and proportion — heavy, short, generously rounded — and `Icons.Filled.Pause`
 * at a matching size is a thinner, wider glyph that reads as a different symbol.
 */
@Composable
private fun PauseGlyph(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val barWidth = size.width * 0.26f
        val gap = size.width * 0.16f
        val barHeight = size.height
        val corner = barWidth / 2f
        drawRoundRect(
            color = color,
            topLeft = Offset(0f, 0f),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(corner),
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(barWidth + gap, 0f),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(corner),
        )
    }
}

/**
 * The live-text stage: one field, one piece of state.
 *
 * The focus callback exists so the room knows when someone is mid-edit — that is what lets the
 * pending-write indicator appear, and what stops an incoming change from yanking the field out
 * from under a person who is typing in it.
 *
 * The chip's characters are marked twice over: heavier, and underlined with a frozen wave. Weight
 * alone proved too weak a signal — the field is already Bold, so "heavier" is a difference
 * noticed on inspection rather than on a glance, and a glance is the only read this gets.
 */
/** The field's own horizontal text inset — the platform's content padding for this text field. */
private val FIELD_TEXT_INSET = 16.dp

/**
 * Where the first line of the field's text begins, measured down from the top of the field.
 *
 * The wave is drawn in the Box *around* the field rather than inside it, so it has to know where
 * the text starts. That offset is the field's vertical content padding, which the platform derives
 * from the text style rather than exposing, so it is pinned here and checked against a render.
 */
private val FIELD_TEXT_TOP_INSET = 12.dp

@Composable
private fun LiveTextField(
    state: RoomUiState,
    onValueChange: (String) -> Unit,
    onEditingChanged: (Boolean) -> Unit,
) {
    val motion = MaterialTheme.pairMotion
    val value = state.draft
    // Null when the chip will carry nothing — an empty field — so the placeholder is never
    // marked as though part of it were going to survive.
    val emphasised = remember(value) { ChipEmphasisTransformation.emphasisedRange(value) }
    val description = if (value.isBlank()) {
        stringResource(R.string.cd_live_text_empty)
    } else {
        stringResource(R.string.cd_live_text_current, value)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // 262dp, measured off the comp: the stage is a large, deliberately empty block, and
            // a field that shrinks to its text would lose the composition entirely.
            .heightIn(min = 262.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                // 262dp, measured off the comp. The height goes on the field rather than on the
                // box behind it, because the card *is* the field's own container: giving the box
                // the height would leave a tall empty region under a short card.
                .heightIn(min = 262.dp)
                .onFocusChanged { onEditingChanged(it.isFocused) }
                .semantics { contentDescription = description },
            textStyle = MaterialTheme.pairTypography.liveText,
            placeholder = {
                // At the field's own size and weight, not at the placeholder defaults. The
                // comp's hint is set as large as the text it stands in for, because this field
                // is a stage rather than a search box: the hint is the sentence it is inviting,
                // not a grey version of a caption.
                Text(
                    text = stringResource(R.string.room_live_text_hint),
                    style = MaterialTheme.pairTypography.liveText,
                    color = PairSurfaces.onCardMuted,
                )
            },
            // Borderless: the field's own container is the stage, and an outline around a filled
            // card reads as two surfaces where the design has one.
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = PairSurfaces.card,
                unfocusedContainerColor = PairSurfaces.card,
                focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
            shape = MaterialTheme.pairShapes.card,
            // The characters the Live Update chip will carry are drawn emphatic, so the field
            // says plainly which part of a long status survives to the one surface it is read
            // on. A visual transformation rather than a second piece of state: the value is
            // untouched, so the cursor, selection and IME all behave as they normally would.
            visualTransformation = ChipEmphasisTransformation,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
            ),
        )

        // The wavy rule under the chip's characters.
        //
        // Weight alone turned out to be a weak signal: the field is already Bold, so
        // "heavier" is a subtle difference at a glance, and on a glance is the only read this
        // ever gets. The wave is the part that actually says "this, and no further" — and it is
        // the same shape the partner line already uses to mark itself, so it reads as Pair's
        // vocabulary rather than a spell-check squiggle. It is drawn frozen, like that one: a
        // travelling wave under a word would be motion that never resolves.
        //
        // Drawn as a sibling rather than inside the field because `TextStyle` has no wavy
        // decoration, and the field's own text is not ours to lay out. The width is measured with
        // the field's own text style so the rule tracks the text exactly — at any font scale, and
        // whether the chip carries six characters or seven.
        if (emphasised != null) {
            val style = MaterialTheme.pairTypography.liveText
            val measurer = rememberTextMeasurer()
            val ruleWidth = with(LocalDensity.current) {
                measurer.measure(
                    text = value.substring(emphasised.first, emphasised.last + 1),
                    style = style,
                    density = this,
                ).size.width.toDp()
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    // The field's own text insets. Horizontal is the platform's 16dp content
                    // padding; vertical is that plus the first line's height, which puts the wave
                    // in the first line's descender space rather than under the second line.
                    .padding(start = FIELD_TEXT_INSET, top = FIELD_TEXT_TOP_INSET)
                    .padding(top = style.lineHeight.value.dp * 0.97f),
            ) {
                IndeterminateWave(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    width = ruleWidth,
                    animated = false,
                )
            }
        }
    }

    // Only while the room is live. When it is paused the indicator already says that nothing is
    // being broadcast, and "Waiting to sync" underneath it would be answering a question the
    // screen has not been asked. When the room is live, the difference between the draft and the
    // published text means the status bar is still showing something stale, and that is worth
    // saying.
    AnimatedVisibility(
        visible = !state.isPaused && state.hasPendingWrite,
        enter = expandVertically(
            animationSpec = motion.fastSpatialSpec(),
        ) + fadeIn(animationSpec = motion.fastEffectsSpec()),
        exit = shrinkVertically(
            animationSpec = motion.fastSpatialSpec(),
        ) + fadeOut(animationSpec = motion.fastEffectsSpec()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Sync,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.connection_syncing),
                style = MaterialTheme.pairTypography.status,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Confirmation before leaving a room, because leaving cannot be undone.
 *
 * Deliberately *not* reachable from the room screen: leaving lives in Settings, and a destructive
 * action belongs with the other account-level actions rather than on the screen the user is
 * working in. It lives in this file because the copy is about the room, not about the account.
 */
@Composable
internal fun RoomLeaveDialog(
    isLeaving: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isLeaving) onDismiss() },
        title = { Text(stringResource(R.string.room_leave_title)) },
        text = { Text(stringResource(R.string.room_leave_body)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = MaterialTheme.pairShapes.button,
            ) {
                if (isLeaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.room_leave))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.join_cancel))
            }
        },
    )
}
