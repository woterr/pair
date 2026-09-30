package com.wood.pair.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wood.pair.R
import com.wood.pair.data.PairGraph
import com.wood.pair.ui.components.PairWordmark
import com.wood.pair.ui.rememberPairViewModel
import com.wood.pair.ui.theme.pairMotion
import com.wood.pair.ui.theme.pairShapes
import com.wood.pair.ui.theme.pairTypography

/**
 * First launch: one field, one button.
 *
 * No account, no email, no password, no phone number. The name is the only thing asked for,
 * because it is the only thing the other person needs to see.
 */
@Composable
fun OnboardingScreen(
    graph: PairGraph,
    onContinue: () -> Unit,
) {
    val viewModel = rememberPairViewModel(graph) { g ->
        OnboardingViewModel(
            preferences = g.preferences,
            auth = g.authRepository,
            roomRepository = g.roomRepository,
            fcmTokenRepository = g.fcmTokenRepository,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.prefill() }
    LaunchedEffect(state.completed) { if (state.completed) onContinue() }

    OnboardingContent(
        state = state,
        onNameChange = viewModel::onNameChange,
        onContinue = viewModel::continueToApp,
    )
}

@Composable
private fun OnboardingContent(
    state: OnboardingUiState,
    onNameChange: (String) -> Unit,
    onContinue: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // The same lockup the rest of the app uses, so the first thing a new user sees is
            // the identity rather than a form with a title above it.
            PairWordmark(partnerName = null)

            Spacer(Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.onboarding_tagline),
                style = MaterialTheme.pairTypography.supporting,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = state.displayName,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.onboarding_name_label)) },
                placeholder = { Text(stringResource(R.string.onboarding_name_hint)) },
                singleLine = true,
                isError = state.error != null,
                supportingText = {
                    AnimatedVisibility(
                        visible = state.error != null,
                        enter = fadeIn(animationSpec = MaterialTheme.pairMotion.fastEffectsSpec()),
                        exit = fadeOut(animationSpec = MaterialTheme.pairMotion.fastEffectsSpec()),
                    ) {
                        Text(text = state.error.asMessage())
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { if (state.canContinue) onContinue() },
                ),
                shape = MaterialTheme.pairShapes.field,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = onContinue,
                enabled = state.canContinue,
                shape = MaterialTheme.pairShapes.button,
                contentPadding = PaddingValues(vertical = 18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.onboarding_continue),
                        style = MaterialTheme.pairTypography.controlLabel,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 8.dp).size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingError?.asMessage(): String = when (this) {
    OnboardingError.NameRequired -> stringResource(R.string.onboarding_name_required)
    OnboardingError.NameTooLong -> stringResource(R.string.onboarding_name_too_long)
    OnboardingError.SessionFailed -> stringResource(R.string.error_auth_failed)
    null -> ""
}
