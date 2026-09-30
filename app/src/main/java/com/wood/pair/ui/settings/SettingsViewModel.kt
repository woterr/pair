package com.wood.pair.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wood.pair.data.local.PreferencesDataSource
import com.wood.pair.data.model.ConnectionState
import com.wood.pair.data.repository.AuthRepository
import com.wood.pair.data.repository.RoomError
import com.wood.pair.data.repository.RoomObservation
import com.wood.pair.data.repository.RoomRepository
import com.wood.pair.data.repository.RoomResult
import com.wood.pair.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val displayName: String = "",
    val currentRoomId: String? = null,
    val avatarId: String? = null,
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val amoledDark: Boolean = true,
    val liveUpdateEnabled: Boolean = true,
    val connection: ConnectionState = ConnectionState.Offline,
    val isLeaving: Boolean = false,
    val isRenaming: Boolean = false,
    val renameDraft: String = "",
    /**
     * Whether this device owns the current room, and whether a partner is still recorded in it.
     *
     * Both are needed to offer "Remove partner" and neither is stored locally: the answer lives
     * in the room record, and a cached copy of it is exactly the kind of thing that goes stale
     * and then offers a button that cannot work.
     */
    val canRemovePartner: Boolean = false,
    val hasPartner: Boolean = false,
    val isRemovingPartner: Boolean = false,
    val partnerRemoved: Boolean = false,
    val error: SettingsError? = null,
)

enum class SettingsError { LeaveFailed, RemovePartnerFailed, NameRequired, NameTooLong }

class SettingsViewModel(
    private val preferences: PreferencesDataSource,
    private val auth: AuthRepository,
    private val roomRepository: RoomRepository,
) : ViewModel() {

    private val _interaction = MutableStateFlow(Interaction())
    private val _error = MutableStateFlow<SettingsError?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        preferences.state,
        roomRepository.connection,
        _interaction,
        _error,
    ) { local, connection, interaction, error ->
        SettingsUiState(
            displayName = local.displayName,
            currentRoomId = local.currentRoomId,
            avatarId = local.avatarId,
            themeMode = local.appearance.themeMode,
            dynamicColor = local.appearance.dynamicColor,
            amoledDark = local.appearance.amoledDark,
            liveUpdateEnabled = local.liveUpdateEnabled,
            connection = connection,
            isLeaving = interaction.isLeaving,
            isRenaming = interaction.isRenaming,
            renameDraft = interaction.renameDraft,
            canRemovePartner = interaction.canRemovePartner,
            hasPartner = interaction.hasPartner,
            isRemovingPartner = interaction.isRemovingPartner,
            partnerRemoved = interaction.partnerRemoved,
            error = error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    /**
     * Keeps [Interaction.canRemovePartner] in step with the room.
     *
     * Only the owner can remove a partner — see [RoomRepository.removePartner] — so the room
     * record is the authority, read continuously rather than cached. A partner who has since
     * left should make the row disappear rather than sit there failing.
     */
    private fun observeRoom() {
        viewModelScope.launch {
            preferences.currentRoomId.collect { roomId ->
                if (roomId == null) {
                    _interaction.update {
                        it.copy(canRemovePartner = false, hasPartner = false)
                    }
                    return@collect
                }
                val uid = auth.currentUidOrNull()
                roomRepository.observeRoom(roomId, uid).collect { observation ->
                    val room = (observation as? RoomObservation.Ready)?.room
                    _interaction.update {
                        it.copy(
                            canRemovePartner = room?.let { r ->
                                r.ownerUid == uid && r.memberUid != null
                            } == true,
                            hasPartner = room?.memberUid != null,
                        )
                    }
                }
            }
        }
    }

    init {
        observeRoom()
    }

    /**
     * Removes the partner from the current room.
     *
     * Reached only when [SettingsUiState.canRemovePartner] is true, so the rules will back it.
     * The room id is kept deliberately: the point is to invite somebody else, not to start
     * again, and the partner's departure should not cost the user their room code.
     */
    fun removePartner(onRemoved: () -> Unit = {}) {
        val roomId = uiState.value.currentRoomId ?: return
        if (_interaction.value.isRemovingPartner) return

        _interaction.update { it.copy(isRemovingPartner = true) }
        viewModelScope.launch {
            when (val result = roomRepository.removePartner(auth.ensureSignedIn(), roomId)) {
                is RoomResult.Success -> {
                    _interaction.update { it.copy(isRemovingPartner = false) }
                    onRemoved()
                }

                is RoomResult.Failure -> {
                    _interaction.update { it.copy(isRemovingPartner = false) }
                    _error.value = SettingsError.RemovePartnerFailed
                }
            }
        }
    }

    private data class Interaction(
        val isLeaving: Boolean = false,
        val isRenaming: Boolean = false,
        val renameDraft: String = "",
        val isRemovingPartner: Boolean = false,
        val partnerRemoved: Boolean = false,
        /** Re-read from the room on every emission; see [SettingsUiState.canRemovePartner]. */
        val canRemovePartner: Boolean = false,
        val hasPartner: Boolean = false,
    )

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch {
        preferences.setThemeMode(mode)
    }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch {
        preferences.setDynamicColor(enabled)
    }

    fun setAmoledDark(enabled: Boolean) = viewModelScope.launch {
        preferences.setAmoledDark(enabled)
    }

    fun setLiveUpdateEnabled(enabled: Boolean) = viewModelScope.launch {
        preferences.setLiveUpdateEnabled(enabled)
    }

    /**
     * Records the chosen avatar.
     *
     * Local only: the avatar is a personal preference, not something the partner needs, and
     * mirroring it would put a drawable name in a shared room node for no benefit.
     */
    fun setAvatar(avatarId: String) = viewModelScope.launch {
        preferences.setAvatarId(avatarId)
    }

    fun startRename() {
        _interaction.update { it.copy(isRenaming = true, renameDraft = it.renameDraft) }
        _error.value = null
    }

    fun onRenameDraftChange(value: String) {
        _interaction.update { it.copy(renameDraft = value) }
    }

    fun cancelRename() {
        _interaction.update { it.copy(isRenaming = false, renameDraft = "") }
    }

    /** Applies a name change locally and mirrors it to the database. */
    fun confirmRename() {
        val draft = _interaction.value.renameDraft.trim()
        if (draft.isEmpty()) {
            _error.value = SettingsError.NameRequired
            return
        }
        if (draft.length > 40) {
            _error.value = SettingsError.NameTooLong
            return
        }
        viewModelScope.launch {
            preferences.setDisplayName(draft)
            // Best-effort: the name inside the room is what the partner reads, and a failure
            // here should not block the local change.
            runCatching {
                val uid = auth.ensureSignedIn()
                roomRepository.setDisplayName(uid, draft)
            }
            _interaction.update { it.copy(isRenaming = false, renameDraft = "") }
        }
    }

    fun leaveRoom(onLeft: (String) -> Unit) {
        val roomId = uiState.value.currentRoomId ?: return
        if (_interaction.value.isLeaving) return

        _interaction.update { it.copy(isLeaving = true) }
        viewModelScope.launch {
            val result = roomRepository.leaveRoom(auth.ensureSignedIn(), roomId)
            _interaction.update { it.copy(isLeaving = false) }
            when (result) {
                is RoomResult.Success -> {
                    preferences.setCurrentRoomId(null)
                    onLeft(roomId)
                }

                is RoomResult.Failure -> {
                    // A room that has already gone is not a failure worth reporting; the local
                    // state should be cleared either way.
                    val gone = result.error == RoomError.NotFound
                    preferences.setCurrentRoomId(null)
                    if (gone) onLeft(roomId) else _error.value = SettingsError.LeaveFailed
                }
            }
        }
    }

    fun dismissError() {
        _error.value = null
    }
}
