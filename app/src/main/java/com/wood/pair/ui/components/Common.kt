package com.wood.pair.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.wood.pair.R
import com.wood.pair.ui.theme.pairTypography

/**
 * The room ID, rendered as the prominent code it is.
 *
 * Users read this out loud, so it gets one of the app's strongest type roles and is given a
 * content description that includes the code verbatim — a screen reader should not have to
 * spell out a six-character identifier letter by letter.
 */
@Composable
fun RoomIdText(
    roomId: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
) {
    // Resolved outside the semantics block, which is not a composable scope.
    val description = stringResource(R.string.cd_room_id, roomId)
    Text(
        text = roomId,
        style = MaterialTheme.pairTypography.roomId,
        color = color,
        modifier = modifier.semantics { contentDescription = description },
    )
}
