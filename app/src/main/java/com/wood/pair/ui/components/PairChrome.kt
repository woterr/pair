package com.wood.pair.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wood.pair.R
import com.wood.pair.ui.shape.BlobShape
import com.wood.pair.ui.shape.StarburstShape
import com.wood.pair.ui.shape.WavyDivider
import com.wood.pair.ui.theme.pairShapes
import com.wood.pair.ui.theme.pairTypography
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import com.wood.pair.ui.theme.PairSurfaces

/**
 * The app's top bar: the mark on the left, the person's name and avatar on the right.
 *
 * Present on every screen except Settings, which uses its own back-and-title bar.
 */
@Composable
fun PairTopBar(
    displayName: String,
    @DrawableRes avatarResId: Int?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TopBarHeight)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PairWordmarkSmall()
        Spacer(Modifier.weight(1f))
        Text(
            text = displayName,
            style = MaterialTheme.pairTypography.screenTitle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 10.dp),
        )
        // 36dp with a 1.5dp ring, where it was a 42dp disc with no edge at all.
        //
        // The size: at 42dp the avatar was taller than the 24dp display name beside it, so the
        // right-hand end of the bar was visually heavier than the left and the name stopped being
        // the thing you read. 36dp keeps the avatar clearly a chip on the name rather than a
        // portrait next to it.
        //
        // The ring: without one the avatar is a soft-edged photo floating on the page background,
        // and its boundary is only findable because the artwork happens to contrast. A hairline in
        // `outlineVariant` gives it an edge that holds on any palette - including the monochrome
        // one, where a white avatar on a black page has no edge of its own. `outlineVariant` and
        // not `primary`, so it reads as a boundary rather than as a selection.
        Avatar(
            resId = avatarResId,
            size = 36.dp,
            initial = displayName,
            borderWidth = 1.5.dp,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

/**
 * Height of the shared top bar.
 *
 * Fixed rather than derived from its content, so that "centred" names one specific line for
 * everything on it. Without a fixed height each child centres on its own box and the row reads
 * as several unrelated items sitting at slightly different heights.
 *
 * Sized to the comps, where the bar's content measures 35dp from the top of the frame; the extra
 * is the breathing room the comp leaves above and below it.
 */
private val TopBarHeight = 56.dp

/**
 * The compact wordmark used in the top bar: the blob, then the wordmark artwork.
 *
 * The artwork is the design's own `pair.svg`, so the star over the "i" is welded to the stem
 * rather than positioned next to it — no measurement, no overlay, and nothing to drift when the
 * screen density or the system font scale changes.
 *
 * The two parts are aligned on their *optical* centres rather than their boxes. The blob is a
 * rounded triangle whose mass sits low, and the wordmark has a star rising above the cap line;
 * centring both boxes puts the blob noticeably high and the wordmark noticeably low. Nudging
 * each by the fraction of its own height that puts its visual centre on the row's centre is what
 * makes the pair read as one mark.
 */
@Composable
fun PairWordmarkSmall(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The blob is laid out in a box of [BlobBox] and the artwork is *scaled* inside it to
        // fill the part of that box which is actually ink.
        //
        // The alternative — laying the box out and insetting it — cannot work, because `Image`
        // does not clip: a non-square box with a square-drawing painter leaves the padding in
        // the layout either way, and the only way to pull the two edges back in is to change
        // what is drawn. Scaling achieves that, and the 0.553 factor is the ratio the exported
        // artwork actually has.
        Image(
            painter = painterResource(R.drawable.ic_pair_blob),
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
            // No graphicsLayer scale. Scaling a layer scales what is *drawn* but not the box the
            // layout reserved, so it shrinks the mark inside a box that stays the size of
            // [BlobBox] — which is the opposite of the fix. The box is already [BlobBox], and the
            // artwork inside it draws at [BLOB_INK] of that, so the drawn mark is the intended
            // size with no further scaling at all.
            modifier = Modifier
                .size(BlobBox)
                .offset(y = BlobBox * BLOB_OPTICAL_NUDGE),
        )
        Spacer(Modifier.width(BlobWordmarkGap))
        Image(
            painter = painterResource(R.drawable.ic_pair_wordmark),
            contentDescription = stringResource(R.string.app_name),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .height(WordmarkHeight)
                .width(WordmarkHeight * (430f / 153f))
                .offset(y = WordmarkHeight * WORDMARK_OPTICAL_NUDGE),
        )
    }
}

private val WordmarkHeight = 28.dp

/**
 * The box that makes the logo the same visual size as the wordmark.
 *
 * Two things had to be measured to get this right, and both are properties of the exported
 * artwork rather than of the design:
 *
 *  - `ic_pair_blob` has a 1249-unit viewport but its ink spans only 691 of it, so the drawn
 *    blob is 0.553 of whatever box it is given.
 *  - the wordmark's ink spans its 430x153 viewport in full, so it is 1.0 of its box.
 *
 * The wordmark is therefore 28dp tall and fills it. The blob needs a box of
 * `28 / 0.553` to fill the same 28dp, which is what [BlobBox] is. Sizing the blob's box to 28dp
 * like everything else — which is what this did before — draws it at 15.5dp and is why the logo
 * looked too small next to the text.
 */
private val BLOB_INK = 691f / 1249f

/**
 * Scales the logo back so the two marks are the same *optical* height.
 *
 * Measured on a real device screenshot rather than by eye: the logo's cap height came out at
 * 20.19dp against the wordmark's 18.67dp, a ratio of 1.08. A blob is a rounded triangle whose
 * mass sits low, so at a merely-matched box it still reads slightly larger than the letters
 * beside it, and this is the factor that takes that difference out.
 */
private const val BLOB_OPTICAL_SCALE = 18.67f / 19.05f

/**
 * The box the blob is laid out in.
 *
 * This is the box that makes the *drawn* blob the same height as the wordmark: the artwork
 * inside it occupies [BLOB_INK] of whatever box it is given, so the box has to be larger than
 * the target height by exactly that factor, and then scaled back by [BLOB_OPTICAL_SCALE] for the
 * fact that a blob's mass sits low and reads slightly heavy next to letters.
 *
 * The wordmark, by contrast, fills its viewport, so it needs no such correction.
 */
private val BlobBox = (WordmarkHeight / BLOB_INK) * BLOB_OPTICAL_SCALE

/**
 * The gap between the logo and the wordmark.
 *
 * 9dp measured from the *drawn* edge of the blob to the start of the letters, which is what
 * makes the pair read as one mark. The blob is laid out in a box of [BlobBox] whose sides are
 * 22% empty margin, so the spacer has to be shortened by that margin as well or the visible
 * gap comes out 2 x 22% wider than intended.
 */
private val BlobWordmarkGap = 9.dp - BlobBox * (1f - BLOB_INK)

/**
 * Optical centring nudges, as fractions of each mark's own height.
 *
 * Measured by eye against the comps, and deliberately expressed as fractions so the alignment
 * survives a change of size. Positive moves the mark down the row.
 */
private const val BLOB_OPTICAL_NUDGE = 0.06f
private const val WORDMARK_OPTICAL_NUDGE = (-0.04f)

/**
 * The full wordmark lockup: the wordmark, "with your", then the partner's name in the serif face.
 *
 * The three lines are one composition. The wordmark is artwork, so its width is known exactly and
 * the two lines below are indented by fractions of it — which is how the comp places them, and
 * what keeps the lockup's proportions intact at any density or font scale.
 *
 * The block is left-aligned internally and centred by the caller, so "Partner" — the widest line
 * — is what the centring acts on. Centring each line individually would scatter the lockup.
 *
 * @param align how the lines sit against each other. [Alignment.Start] is the design's own
 *   arrangement: the wordmark leads, and the two lines below step right.
 */
@Composable
fun PairWordmark(
    partnerName: String?,
    modifier: Modifier = Modifier,
    align: Alignment.Horizontal = Alignment.Start,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = align,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_pair_wordmark),
            contentDescription = stringResource(R.string.app_name),
            // The lockup is the app's one piece of self-promotion, and the comps draw it in the
            // theme's own accent rather than in body ink: the mark is the thing on the screen
            // that is meant to be looked at, and `onSurface` would make it read as body text.
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .width(WORDMARK_WIDTH)
                .aspectRatio(430f / 153f)
                .semantics { heading() },
        )
        Text(
            text = stringResource(R.string.wordmark_sub),
            style = MaterialTheme.pairTypography.wordmarkSub,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = WORDMARK_WIDTH * SUB_INDENT),
        )
        Text(
            text = partnerName ?: stringResource(R.string.wordmark_partner),
            style = MaterialTheme.pairTypography.partnerWord,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = WORDMARK_WIDTH * PARTNER_INDENT),
        )
    }
}

/** Rendered width of the wordmark artwork. Height follows from the artwork's own aspect ratio. */
private val WORDMARK_WIDTH = 163.dp

/**
 * The two indents, as fractions of the wordmark's width, measured off the comps.
 *
 * Expressed in these units rather than as absolute offsets so the lockup survives a change of
 * artwork, density, or the user's system font-scale setting.
 */
private const val SUB_INDENT = 0.68f
private const val PARTNER_INDENT = 1.02f

/** A circular avatar, falling back to the initial when no image has been chosen yet. */
@Composable
fun Avatar(
    @DrawableRes resId: Int?,
    size: Dp,
    modifier: Modifier = Modifier,
    initial: String = "",
    /** Draws the selected ring, as the Settings grid does. */
    selected: Boolean = false,
    /**
     * An always-on hairline ring, for the places that are not a selection.
     *
     * Separate from [selected] on purpose: a selection ring is 2dp in `primary` and inset, because
     * it has to read as "chosen". This is a boundary, not a state, so it is thinner and neutral,
     * and it sits outside the artwork rather than eating into it.
     */
    borderWidth: Dp = 0.dp,
    borderColor: Color = Color.Transparent,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            // CircleShape, not `pairShapes.pill`. Pill is a `RoundedCornerShape(percent = 50)`,
            // which is only circular while the box it is applied to is square. The picker lays
            // avatars out in a grid whose cells are sized by the column count, so a cell can end
            // up very slightly non-square and a percentage corner radius then produces an oval.
            // Stating the intent directly removes the dependency on the box's proportions.
            .clip(CircleShape)
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                    Modifier
                },
            )
            .then(
                // After the selection ring, so a selected avatar in the grid never picks up a
                // second neutral edge on top of its primary one.
                if (!selected && borderWidth > 0.dp) {
                    Modifier.border(borderWidth, borderColor, CircleShape)
                } else {
                    Modifier
                },
            )
            .padding(if (selected) 3.dp else 0.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (resId != null) {
            Image(
                painter = painterResource(resId),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .aspectRatio(1f)
                    .size(if (selected) size - 8.dp else size)
                    .clip(CircleShape),
            )
        } else if (initial.isNotBlank()) {
            Text(
                text = initial.take(1).uppercase(),
                style = MaterialTheme.pairTypography.itemTitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The avatar grid on the Settings screen.
 *
 * Lazy so the set can grow without a layout cost, and its height is derived from the row count
 * rather than fixed — a fixed height would leave dead space under the last row the moment the set
 * is not an exact multiple of the column count.
 */
@Composable
fun AvatarPicker(
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 5,
) {
    val label = stringResource(R.string.settings_choose_avatar)
    val rows = (Avatars.all.size + columns - 1) / columns
    val gap = 14.dp
    val height = AvatarSize * rows + gap * (rows - 1)

    // A fixed cell width rather than letting the grid divide the available width. Dividing is
    // what makes each cell a fraction of a pixel different from the next, and `aspectRatio` then
    // resolves each avatar to a marginally different height — which, accumulated down two rows,
    // is a visible drift. One cell width for all of them, and the grid is exactly square.
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        items(Avatars.all, key = { it.id }) { option ->
            val isSelected = option.id == selectedId
            Box(
                modifier = Modifier.size(AvatarSize),
                contentAlignment = Alignment.Center,
            ) {
                Avatar(
                    resId = option.resId,
                    size = AvatarSize,
                    selected = isSelected,
                    modifier = Modifier
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = { onSelect(option.id) },
                        )
                        .semantics { contentDescription = label },
                )
            }
        }
    }
}

/** Size of one avatar in the picker, and of the avatar in the top bar. */
private val AvatarSize = 56.dp

/** A soft organic shape drawn purely as a background accent. Always behind content. */
@Composable
fun DecorativeBlob(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(BlobShape())
            .background(tint),
    )
}

/** The sharp starburst the comps place behind the wordmark. Always behind content. */
@Composable
fun DecorativeStarburst(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(StarburstShape())
            .background(tint),
    )
}

/**
 * A section header: a small icon and a bold label, as on the Settings screen.
 *
 * The label is a real heading for screen readers, which matters because the sections are the
 * screen's only structure.
 */
@Composable
fun SectionHeader(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.pairTypography.sectionLabel,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * The one name-shaped text field in the app.
 *
 * ## Why this exists
 *
 * Two screens ask the same question in the same words — "what is this called?" — in the display
 * name on Settings and the place name on a location rule. They were separate `OutlinedTextField`
 * calls that had already drifted: the rule editor's had an edit pencil bolted into its trailing
 * slot, which does nothing there (the field is always live, there is no separate editing state to
 * enter) and which duplicated a control the row already had.
 *
 * Extracting the box means the two cannot drift again, and it makes the comparison the user asked
 * for true by construction rather than by eye.
 *
 * Deliberately absent:
 *  - a **floating label**. Both call sites already have a heading above the field in the same
 *    position, so a label would appear on focus and shift the layout under the user's finger.
 *  - a **trailing icon**. Nothing here has a second action, and an icon that is not a control
 *    is furniture.
 *  - a **border at rest**. `unfocusedBorderColor` is the card fill, so the field reads as one
 *    filled surface with a focus ring arriving on it, which is how the comps draw it.
 */
@Composable
fun PairNameField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
    trailingContent: (@Composable () -> Unit)? = null,
    focusRequester: FocusRequester? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
        singleLine = true,
        textStyle = MaterialTheme.pairTypography.screenTitle,
        placeholder = { Text(placeholder) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = PairSurfaces.card,
            focusedContainerColor = PairSurfaces.card,
            unfocusedContainerColor = PairSurfaces.card,
        ),
        shape = MaterialTheme.pairShapes.field,
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onDone = { onImeAction() },
            onGo = { onImeAction() },
            onNext = { onImeAction() },
        ),
        trailingIcon = trailingContent,
    )
}

/** A fixed-height gap, for the comps' deliberate empty space. */
@Composable
fun VerticalGap(height: Dp) {
    Spacer(Modifier.height(height))
}

/** Convenience: content that must not be read separately from its parent. */
@Composable
fun DecorativeOnly(content: @Composable () -> Unit) {
    Box(modifier = Modifier.clearAndSetSemantics { }) { content() }
}
