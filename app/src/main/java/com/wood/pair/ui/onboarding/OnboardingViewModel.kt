package com.wood.pair.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wood.pair.data.local.PreferencesDataSource
import com.wood.pair.data.repository.AuthRepository
import com.wood.pair.data.repository.FcmTokenRepository
import com.wood.pair.data.repository.RoomRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** State of the "what should we call you?" screen. */
data class OnboardingUiState(
    val displayName: String = "",
    val isSubmitting: Boolean = false,
    val error: OnboardingError? = null,
    val completed: Boolean = false,
) {
    val canContinue: Boolean get() = displayName.isNotBlank() && !isSubmitting
}

enum class OnboardingError {
    NameRequired,
    NameTooLong,
    SessionFailed,
}

/**
 * First launch: capture a display name and establish the anonymous session.
 *
 * There is deliberately no email, password, phone number or account. The name is local
 * convenience; identity is the anonymous Firebase UID.
 */
class OnboardingViewModel(
    private val preferences: PreferencesDataSource,
    private val auth: AuthRepository,
    private val roomRepository: RoomRepository,
    private val fcmTokenRepository: FcmTokenRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    /** Pre-fills the field when onboarding is revisited to change the name. */
    suspend fun prefill() {
        val existing = preferences.displayName.first()
        if (existing.isNotBlank() && _uiState.value.displayName.isBlank()) {
            _uiState.value = _uiState.value.copy(displayName = existing)
        }
    }

    fun onNameChange(value: String) {
        _uiState.update { state ->
            // Clear a "name required" error as soon as the user starts typing; leave other
            // errors up so the retry affordance stays meaningful.
            val error = if (state.error == OnboardingError.NameRequired) null else state.error
            state.copy(displayName = value, error = error)
        }
    }

    fun continueToApp() {
        val name = _uiState.value.displayName.trim()
        if (name.isEmpty()) {
            _uiState.update { it.copy(error = OnboardingError.NameRequired) }
            return
        }
        if (name.length > MAX_NAME_LENGTH) {
            _uiState.update { it.copy(error = OnboardingError.NameTooLong) }
            return
        }

        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            val uid = runCatching { auth.ensureSignedIn() }.getOrElse {
                _uiState.update {
                    it.copy(isSubmitting = false, error = OnboardingError.SessionFailed)
                }
                return@launch
            }

            preferences.setDisplayName(name)
            // Mirroring the name is best-effort: onboarding must not be blocked by it, and the
            // room copy is the one a partner actually reads.
            runCatching { roomRepository.setDisplayName(uid, name) }
            // Register this device for pushes so remote Live Updates can reach it.
            runCatching { fcmTokenRepository.syncToken() }

            _uiState.update { it.copy(isSubmitting = false, completed = true) }
        }
    }

    companion object {
        const val MAX_NAME_LENGTH: Int = 40
    }
}
