package com.wood.pair.notifications

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.wood.pair.R
import com.wood.pair.data.model.RoomId
import com.wood.pair.ui.theme.PairTheme
import kotlinx.coroutines.launch

/**
 * A small "set your status" surface, opened by tapping the Live Update.
 *
 * ## Why this exists when the notification already has an inline field
 *
 * It has one, and it is built and attached — the app logs `2 action(s), 1 with an inline text
 * field`. It is simply not rendered.
 *
 * A *promoted* notification — a Live Update — is drawn by the platform's own template, and that
 * template does not show inline text-entry actions. Turn Live Updates off and the same
 * notification is an ordinary ongoing one, the inline field appears, and it works. So the two
 * features the product is built on are mutually exclusive on this platform: the status bar and AOD
 * chip, or a tappable reply field in the notification.
 *
 * The chip is the product. It is the one surface that is always showing, it is the reason the
 * database rules exist, and it is what the whole foreground-service work was for. So the chip
 * wins, and this is the way to set a status from the notification anyway.
 *
 * ## Why an Activity at all
 *
 * Because there is no API to render arbitrary content into the notification, and a body tap has
 * to go somewhere. A transparent Activity over whatever was on screen is the standard answer: it
 * costs one tap, shows one field, and closes. The alternative the app had was to deep-link into
 * the room, which is what this replaces — the original request was to change the status *from the
 * notification* rather than open the app, and this is that.
 *
 * Being honest about the cost: this does put a window between the user and what they were doing.
 * It is confined to a single small card, it does not launch the app's UI, and it closes itself.
 * The inline field, where the platform renders it, is still preferable and is still attached — a
 * user with Live Updates off gets the tidier path for free.
 */
class QuickStatusActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val roomId = intent?.getStringExtra(EXTRA_ROOM_ID)
        if (roomId == null || !RoomId.isValid(roomId)) {
            // Nothing sensible to show, and nothing worth leaving on screen.
            finish()
            return
        }

        setContent {
            PairTheme {
                QuickStatusSheet(
                    onSend = { text -> submit(roomId, text) },
                    onDismiss = { finish() },
                )
            }
        }
    }

    private fun submit(roomId: String, text: String) {
        StatusSubmitter.submit(
            context = this,
            roomId = roomId,
            text = text,
            scope = StatusSubmitter.transientScope(),
            onDone = { finish() },
        )
    }

    companion object {
        /** Must match the extra the notification's body intent is built with. */
        const val EXTRA_ROOM_ID = "roomId"
    }
}

/**
 * The sheet itself, split out so it renders without an Activity and can be reasoned about.
 *
 * Deliberately not a dialog: a real [android.app.Dialog] with a transparent Activity behind it
 * fights the IME for the window, and this surface has to sit correctly above the keyboard.
 */
@Composable
private fun QuickStatusSheet(
    onSend: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            // Sits above the keyboard rather than behind it, which is the whole reason this is
            // composed with the IME inset applied.
            .imePadding()
            .padding(16.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.quick_status_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.quick_status_hint)) },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSend = { onSend(text) },
                ),
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = androidx.compose.ui.Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = { onSend(text) }) {
                    Text(stringResource(R.string.quick_status_send))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.quick_status_cancel))
                }
            }
        }
    }
}
