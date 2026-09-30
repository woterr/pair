package com.wood.pair.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wood.pair.data.local.LocalState
import com.wood.pair.data.local.PreferencesDataSource
import com.wood.pair.data.model.ConnectionState
import com.wood.pair.data.repository.AuthRepository
import com.wood.pair.data.repository.RoomError
import com.wood.pair.data.repository.RoomRepository
import com.wood.pair.data.repository.RoomResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val displayName: String = "",
    /** A room the user is already in, so the screen can offer to return to it. */
    val currentRoomId: String? = null,
    /** Which avatar the user picked, or null if they have not chosen one. */
    val avatarId: String? = null,
    val connection: ConnectionState = ConnectionState.Offline,
    val isCreating: Boolean = false,
    val isJoining: Boolean = false,
    val joinSheetVisible: Boolean = false,
    val joinInput: String = "",
    val error: HomeError? = null,
) {
    val isBusy: Boolean get() = isCreating || isJoining
    val hasRoom: Boolean get() = currentRoomId != null
}

enum class HomeError {
    RoomNotFound,
    RoomFull,
    InvalidRoomId,
    Offline,
    TimedOut,
    CreateFailed,
    JoinFailed,
    SessionFailed,
}

class HomeViewModel(
    private val preferences: PreferencesDataSource,
    private val auth: AuthRepository,
    private val roomRepository: RoomRepository,
) : ViewModel() {

    private val _local = MutableStateFlow(LocalState())
    private val _interaction = MutableStateFlow(
        InteractionState(isCreating = false, isJoining = false, joinSheetVisible = false),
    )
    private val _joinInput = MutableStateFlow("")
    private val _error = MutableStateFlow<HomeError?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        _local,
        roomRepository.connection,
        _interaction,
        _joinInput,
        _error,
    ) { local, connection, interaction, joinInput, error ->
        HomeUiState(
            displayName = local.displayName,
            currentRoomId = local.currentRoomId,
            avatarId = local.avatarId,
            connection = connection,
            isCreating = interaction.isCreating,
            isJoining = interaction.isJoining,
            joinSheetVisible = interaction.joinSheetVisible,
            joinInput = joinInput,
            error = error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = HomeUiState(),
    )

    private data class InteractionState(
        val isCreating: Boolean = false,
        val isJoining: Boolean = false,
        val joinSheetVisible: Boolean = false,
    )

    init {
        viewModelScope.launch {
            preferences.state.collect { _local.value = it }
        }
    }

    fun showJoinSheet() {
        _interaction.update { it.copy(joinSheetVisible = true) }
        _error.value = null
    }

    fun dismissJoinSheet() {
        _interaction.update { it.copy(joinSheetVisible = false) }
        _error.value = null
    }

    fun onJoinInputChange(value: String) {
        _joinInput.value = value.uppercase()
        _error.value = null
    }

    fun createRoom(onCreated: (String) -> Unit) {
        if (_interaction.value.isCreating) return
        _interaction.update { it.copy(isCreating = true) }
        _error.value = null

        viewModelScope.launch {
            val name = _local.value.displayName
            val uid = runCatching { auth.ensureSignedIn() }.getOrElse {
                _interaction.update { it.copy(isCreating = false) }
                _error.value = HomeError.SessionFailed
                return@launch
            }

            when (val result = roomRepository.createRoom(uid, name)) {
                is RoomResult.Success -> {
                    preferences.setCurrentRoomId(result.value)
                    _interaction.update { it.copy(isCreating = false) }
                    onCreated(result.value)
                }

                is RoomResult.Failure -> {
                    _interaction.update { it.copy(isCreating = false) }
                    _error.value = result.error.toHomeError(fallback = HomeError.CreateFailed)
                }
            }
        }
    }

    fun joinRoom(onJoined: (String) -> Unit) {
        if (_interaction.value.isJoining) return
        _interaction.update { it.copy(isJoining = true) }
        _error.value = null

        viewModelScope.launch {
            val name = _local.value.displayName
            val input = _joinInput.value
            val uid = runCatching { auth.ensureSignedIn() }.getOrElse {
                _interaction.update { it.copy(isJoining = false) }
                _error.value = HomeError.SessionFailed
                return@launch
            }

            when (val result = roomRepository.joinRoom(uid, name, input)) {
                is RoomResult.Success -> {
                    preferences.setCurrentRoomId(result.value)
                    _interaction.update { it.copy(isJoining = false, joinSheetVisible = false) }
                    onJoined(result.value)
                }

                is RoomResult.Failure -> {
                    _interaction.update { it.copy(isJoining = false) }
                    _error.value = result.error.toHomeError(fallback = HomeError.JoinFailed)
                }
            }
        }
    }

    /** Clears a room the user is no longer in, e.g. after the room vanished server-side. */
    fun forgetRoom(roomId: String) {
        viewModelScope.launch {
            if (_local.value.currentRoomId == roomId) {
                preferences.setCurrentRoomId(null)
            }
        }
    }

    fun dismissError() {
        _error.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

internal fun RoomError.toHomeError(fallback: HomeError): HomeError = when (this) {
    RoomError.NotFound -> HomeError.RoomNotFound
    RoomError.Full -> HomeError.RoomFull
    is RoomError.InvalidId -> HomeError.InvalidRoomId
    is RoomError.Offline -> HomeError.Offline
    RoomError.TimedOut -> HomeError.TimedOut
    is RoomError.Auth -> HomeError.SessionFailed
    is RoomError.Unknown -> fallback
    RoomError.NotAMember -> fallback
}
