package com.wood.pair.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Science
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wood.pair.R
import com.wood.pair.debug.PeerDeviceController
import com.wood.pair.debug.PeerDevices
import com.wood.pair.ui.components.SectionHeader
import com.wood.pair.ui.theme.pairShapes
import com.wood.pair.ui.theme.pairTypography
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment

/**
 * Debug-only panel for driving the whole product from one phone.
 *
 * The second device below is a real second Pair identity — its own anonymous account and its
 * own FCM registration — so pairing, the claim transaction, the security rules, the debounce,
 * the listener and the FCM round trip are all exercised for real. Nothing here is mocked, and
 * the whole panel is absent from release builds.
 */
@Composable
fun PeerSection(
    peer: PeerDeviceController,
    currentRoomId: String?,
    modifier: Modifier = Modifier,
) {
    val state by peer.state.collectAsStateWithLifecycle()
    var joinInput by remember { mutableStateOf(currentRoomId.orEmpty()) }
    var textInput by remember { mutableStateOf("") }
    val defaultPeerName = stringResource(R.string.peer_default_name)

    LaunchedEffect(Unit) { peer.start() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // The one divider above this panel. Settings used to draw another one immediately before
        // calling this, which put two rules on top of each other with nothing between them but
        // 16dp — visible as a doubled line under the "Leave room" row. The panel is
        // self-contained, so it owns its own separation rather than relying on the screen above
        // it to supply one.
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SectionHeader(
            icon = Icons.Default.Science,
            text = stringResource(R.string.peer_title),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.peer_explain)) },
            supportingContent = {
                Text(
                    text = state.lastError
                        ?: state.message.ifBlank { stringResource(R.string.peer_signed_out) },
                    style = MaterialTheme.pairTypography.status,
                    color = if (state.lastError != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )

        if (!state.isSignedIn) {
            FilledTonalButton(
                onClick = { peer.signIn(defaultPeerName) },
                shape = MaterialTheme.pairShapes.button,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Text(stringResource(R.string.peer_sign_in))
            }
        } else {
            OutlinedTextField(
                value = joinInput,
                onValueChange = { joinInput = it.uppercase() },
                label = { Text(stringResource(R.string.peer_join_label)) },
                singleLine = true,
                shape = MaterialTheme.pairShapes.field,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { peer.join(joinInput, state.displayName) },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { peer.join(joinInput, state.displayName) },
                    enabled = joinInput.isNotBlank() && !state.isBusy,
                    shape = MaterialTheme.pairShapes.button,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.peer_join))
                }
                FilledTonalButton(
                    onClick = { peer.leave() },
                    enabled = state.isPaired,
                    shape = MaterialTheme.pairShapes.button,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.peer_leave))
                }
            }

            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                label = { Text(stringResource(R.string.peer_send_label)) },
                singleLine = true,
                shape = MaterialTheme.pairShapes.field,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        state.roomId?.let { peer.sendLiveText(it, textInput) }
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        state.roomId?.let { roomId ->
                            peer.sendLiveText(roomId, textInput)
                            peer.postLiveUpdate(roomId, textInput)
                        }
                    },
                    enabled = state.isPaired && !state.isBusy,
                    shape = MaterialTheme.pairShapes.button,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.peer_send))
                }
                FilledTonalButton(
                    onClick = { peer.reset() },
                    shape = MaterialTheme.pairShapes.button,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.peer_reset))
                }
            }

            // Off by default, and the label says exactly what turning it on will do. With it off,
            // sending a message produces one notification — the app's — carrying the peer's words,
            // which is the product. Turning it on adds a second one so the peer's own Live
            // Update can be inspected, which is a different question.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.peer_own_notification),
                        style = MaterialTheme.pairTypography.itemTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.peer_own_notification_summary),
                        style = MaterialTheme.pairTypography.status,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.postsOwnLiveUpdate,
                    onCheckedChange = peer::setPostsOwnLiveUpdate,
                )
            }
        }
    }
}
