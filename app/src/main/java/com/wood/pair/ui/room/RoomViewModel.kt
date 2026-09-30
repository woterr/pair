package com.wood.pair.ui.room

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wood.pair.core.launchSafely
import com.wood.pair.data.local.PreferencesDataSource
import com.wood.pair.data.model.ConnectionState
import com.wood.pair.data.model.LiveTexts
import com.wood.pair.data.model.Room
import com.wood.pair.data.repository.AuthRepository
import com.wood.pair.data.repository.RoomError
import com.wood.pair.data.repository.RoomObservation
import com.wood.pair.data.repository.RoomRepository
import com.wood.pair.data.repository.RoomResult
import com.wood.pair.notifications.LiveUpdateDecision
import com.wood.pair.notifications.LiveUpdateService
import com.wood.pair.notifications.decideLiveUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the room screen renders. */
data class RoomUiState(
    val roomId: String = "",
    val room: Room? = null,
    /** The signed-in user's own name, for the top bar. */
    val displayName: String = "",
    /** The signed-in user's own avatar, for the top bar. */
    val avatarId: String? = null,
    val connection: ConnectionState = ConnectionState.Offline,
    /** Text currently in the editor. Yours alone; the partner's text never lands here. */
    val draft: String = "",
    /** What the database currently holds for *this* user, i.e. what the partner is reading. */
    val myPublishedText: String = "",
    /** What the partner is currently broadcasting, i.e. what goes in *this* device's Live Update. */
    val partnerText: String = "",
    val isPartnerPresent: Boolean = false,
    val partnerName: String? = null,
    val isEditing: Boolean = false,
    val isLeaving: Boolean = false,
    val leaveDialogVisible: Boolean = false,
    val liveUpdateEnabled: Boolean = true,
    val isLoading: Boolean = true,
    /**
     * The text most recently published, while its confirmation chip is still showing.
     *
     * Null otherwise. Raised on a successful write and cleared on a timer by the view model, so
     * the screen never has to own a "hide me in two seconds" job that can outlive its
     * composition.
     */
    val sentText: String? = null,
    val error: RoomScreenError? = null,
) {
    /** True when the local draft has not yet been published. */
    val hasPendingWrite: Boolean get() = draft != myPublishedText

    /**
     * Whether "Set status" would do anything.
     *
     * False in both of the cases where the button would be a lie:
     *
     *  - **The field is empty.** There is no status to send. Pressing would write an empty string,
     *    which is the same as clearing, so the button would read as "set" while clearing.
     *  - **The draft already matches what is published.** The write would be a no-op, and the
     *    partner's notification would not change. A button that is live but has no effect is the
     *    thing this exists to stop.
     *
     * This is also what makes the button *become* the confirmation. Pressing it drives the draft
     * and the published value together, so the button disabling itself afterwards is the signal
     * that the status went out - which is why [sentText] is a nicety on top rather than the
     * mechanism.
     */
    val canCommit: Boolean get() = draft.isNotBlank() && hasPendingWrite

    /**
     * The status this device's Live Update is showing, or blank when there is none.
     *
     * With a partner, that is the partner's status: the notification answers "where is the person
     * I am paired with". With no partner it is the viewer's own, because that is the only status
     * in existence and the rule is that what you write is shown to you until someone joins.
     */
    val notifiedText: String get() = if (isPartnerPresent) partnerText else myPublishedText

    /**
     * True when there is no status to broadcast.
     *
     * This is the "paused" state, and it is deliberately the same condition the Live Update keys
     * off, so the indicator and the notification can never contradict each other on screen.
     *
     * Keyed on published text alone and never on [draft]: the Live Update shows what the database
     * holds, so a draft that has not been saved cannot un-pause anything.
     */
    val isPaused: Boolean get() = notifiedText.isBlank()
}

/** Errors the room screen can surface. */
enum class RoomScreenError {
    Unavailable,
    LeaveFailed,
    TimedOut,
    Offline,
}

/** One-shot signals the UI acts on, such as "navigate away". */
sealed interface RoomEvent {
    data class LeftRoom(val roomId: String) : RoomEvent
    data object RoomGone : RoomEvent
}

class RoomViewModel(
    private val application: Application,
    private val roomId: String,
    private val preferences: PreferencesDataSource,
    private val auth: AuthRepository,
    private val roomRepository: RoomRepository,
) : ViewModel() {

    private val observation = MutableStateFlow<RoomObservation>(RoomObservation.Loading)
    private val draft = MutableStateFlow("")
    private val editing = MutableStateFlow(false)
    private val sentText = MutableStateFlow<String?>(null)
    private val pendingCommit = MutableStateFlow<String?>(null)
    private val loading = MutableStateFlow(true)
    private val leaving = MutableStateFlow(false)
    private val leaveDialog = MutableStateFlow(false)
    private val error = MutableStateFlow<RoomScreenError?>(null)
    private val displayName = MutableStateFlow("")
    private val avatarId = MutableStateFlow<String?>(null)

    private val _events = MutableSharedFlow<RoomEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<RoomEvent> = _events.asSharedFlow()

    /**
     * The debounced live-text write.
     *
     * Typing updates the UI instantly; this job is restarted on every keystroke so exactly one
     * Firebase write happens per settled pause in typing, not one per character.
     */
    private var pendingWrite: Job? = null

    /** The job that takes the "status sent" chip back down. See [showSentConfirmation]. */
    private var sentNoticeJob: Job? = null

    /** Last value written by this device, used to avoid redundant writes. */
    private var lastWrittenText: String? = null

    /**
     * Notifier work is dispatched off the main thread (it is a binder call) and allowed to
     * fail: a notification that cannot be posted must never take the app down.
     */
    private val notificationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The value last shown in the Live Update, so the notification is only rebuilt on change. */
    private var lastNotifiedText: String? = null

    private val uid: String? get() = auth.currentUidOrNull()

    /** Everything the screen needs from local preferences, bundled to keep the arity sane. */
    private data class LocalBundle(
        val liveUpdateEnabled: Boolean,
        val displayName: String,
        val avatarId: String?,
    )

    private val local = MutableStateFlow(LocalBundle(true, "", null))

    /**
     * Transient screen state, bundled separately from the room observation.
     *
     * Two `combine` passes rather than one wide one: the typed `combine` overloads stop at five
     * sources, and nesting keeps the arity legal without resorting to an untyped `Array<Any>`
     * that would erase every parameter type.
     */
    private data class TransientBundle(
        val isLoading: Boolean,
        val isLeaving: Boolean,
        val leaveDialogVisible: Boolean,
        val error: RoomScreenError?,
    )

    private data class RoomBundle(
        val room: Room?,
        val connection: ConnectionState,
        val editor: EditorBundle,
        val local: LocalBundle,
    )

    /**
     * Everything that belongs to the editor rather than to the room.
     *
     * Grouped so [roomBundle] can stay within the typed `combine` arity. The three are also the
     * three that change together — typing, focus and the sent confirmation are all consequences
     * of the same interaction — so treating them as one unit is honest, not just a workaround.
     */
    private data class EditorBundle(
        val draft: String,
        val isEditing: Boolean,
        val sentText: String?,
        val pendingCommit: String?,
    )

    private val editorBundle: StateFlow<EditorBundle> = combine(draft, editing, sentText, pendingCommit) { d, e, s, p ->
        EditorBundle(draft = d, isEditing = e, sentText = s, pendingCommit = p)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = EditorBundle(draft = "", isEditing = false, sentText = null, pendingCommit = null),
    )

    private val roomBundle: StateFlow<RoomBundle> = combine(
        observation,
        roomRepository.connection,
        editorBundle,
        local,
    ) { obs, connection, editor, localState ->
        RoomBundle(
            room = (obs as? RoomObservation.Ready)?.room,
            connection = connection,
            editor = editor,
            local = localState,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = RoomBundle(
            room = null,
            connection = ConnectionState.Offline,
            editor = EditorBundle(draft = "", isEditing = false, sentText = null, pendingCommit = null),
            local = LocalBundle(true, "", null),
        ),
    )

    val uiState: StateFlow<RoomUiState> = combine(
        roomBundle,
        combine(loading, leaving, leaveDialog, error) { isLoading, isLeaving, dialogVisible, err ->
            TransientBundle(isLoading, isLeaving, dialogVisible, err)
        },
    ) { bundle, transient ->
        val room = bundle.room
        val viewer = uid
        val partnerUid = room?.partnerOf(viewer)
        val partnerName = room?.partnerNameOf(viewer).takeUnless { it.isNullOrBlank() }

        RoomUiState(
            roomId = roomId,
            room = room,
            displayName = bundle.local.displayName,
            avatarId = bundle.local.avatarId,
            connection = bundle.connection,
            draft = bundle.editor.draft,
            // The in-flight commit wins over the database's copy, so "Set status" disables on the
            // press rather than ~100ms later when the listener echoes the write back.
            myPublishedText = bundle.editor.pendingCommit
                ?: room?.live?.textOf(viewer).orEmpty(),
            partnerText = room?.live?.textOf(partnerUid).orEmpty(),
            isPartnerPresent = partnerUid != null,
            partnerName = partnerName,
            isEditing = bundle.editor.isEditing,
            sentText = bundle.editor.sentText,
            isLeaving = transient.isLeaving,
            leaveDialogVisible = transient.leaveDialogVisible,
            liveUpdateEnabled = bundle.local.liveUpdateEnabled,
            isLoading = transient.isLoading,
            error = transient.error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = RoomUiState(roomId = roomId),
    )

    init {
        observeRoom()
        observePreferences()
    }

    private fun observeRoom() {
        viewModelScope.launch {
            roomRepository.observeRoom(roomId, uid).collect { next ->
                observation.value = next
                loading.value = false
                when (next) {
                    is RoomObservation.Ready -> onRoomAvailable(next.room)
                    RoomObservation.Unavailable -> {
                        cancelLiveUpdate()
                        // A room we are no longer a member of is not a state to sit on. Clear
                        // the stale local pointer and let the UI go back.
                        preferences.setCurrentRoomId(null)
                        _events.tryEmit(RoomEvent.RoomGone)
                    }

                    RoomObservation.Loading -> Unit
                }
            }
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            preferences.state.collect { prefs ->
                local.value = LocalBundle(
                    liveUpdateEnabled = prefs.liveUpdateEnabled,
                    displayName = prefs.displayName,
                    avatarId = prefs.avatarId,
                )
            }
        }
    }

    private fun onRoomAvailable(room: Room) {
        val viewer = uid
        val mine = room.live.textOf(viewer)

        // The editor is *mine*. It is reconciled with **my own** published value and never with
        // the room's value-in-general, which is what stops a partner's status from landing in my
        // field. Reconciling on the same key the write uses also means a publish that the
        // listener echoes back is a no-op rather than a cursor jump.
        if (!editing.value && draft.value != mine) {
            draft.value = mine
            lastWrittenText = mine
        }

        // The Live Update carries the *partner's* status when there is one, because that is what
        // the notification is for. With no partner it carries mine, so the feature is still
        // observable while you are waiting rather than appearing only once someone arrives.
        //
        // The decision of what to show, and whether to show anything, is made by
        // [decideLiveUpdate] — the same function the notification service uses while the app is
        // closed. One rule, two callers, so the chip cannot disagree with the room screen; and
        // calling it from here as well means the chip reacts on the same frame as the UI rather
        // than one database round trip later.
        onLiveUpdateDecision(
            decideLiveUpdate(
                observation = RoomObservation.Ready(room),
                uid = viewer,
                liveUpdateEnabled = local.value.liveUpdateEnabled,
                currentlyShown = lastNotifiedText,
            ),
        )
    }

    // ---------------------------------------------------------------- editing

    /**
     * Records an edit to the draft. Does **not** publish it.
     *
     * Publishing on every keystroke was wrong in a way that was easy to miss: the status bar
     * changed as you typed, so the thing the partner reads flickered through every
     * intermediate state — and there was no point at which the writer's own text was
     * definitely "the" text. Editing and publishing are now separate acts, and the only way to
     * publish is [commitLiveText].
     */
    fun onLiveTextChange(value: String) {
        draft.value = value.take(Room.MAX_TEXT_LENGTH)
        // Editing invalidates the last confirmation immediately. A chip saying "Sent: going to
        // the shops" while the field has been changed since is a statement about a status that no
        // longer exists.
        if (sentText.value != null) sentText.value = null
        // And it cancels the in-flight claim on the baseline. Typing while a write is in the air
        // means the text about to land is not the text in the field, so the button has to go live
        // again for the new value rather than staying disabled for the old one.
        pendingCommit.value = null
    }

    /**
     * Publishes the draft.
     *
     * Called by "Set status". This is the only write path from the room screen, which is what
     * makes the button's label true. The button is disabled unless there is something to publish
     * — see [RoomUiState.canCommit] — so this cannot be reached with nothing to do.
     *
     * [pendingCommit] is set **before** the coroutine is launched, not in the write's success
     * handler. That is what makes the button react to the press rather than to the round trip:
     * without it the button stayed live for the ~100ms the database took to echo the value back,
     * which is long enough to double-tap and long enough for the press to look like it did
     * nothing. With it, pressing disables the button on the same frame.
     *
     * The chip waits for the write to actually succeed, so the two never disagree: a failure
     * leaves the button live again and shows no confirmation.
     */
    fun commitLiveText() {
        val text = draft.value
        if (text.isBlank() || text == publishedBaseline()) return
        pendingCommit.value = text
        pendingWrite?.cancel()
        pendingWrite = viewModelScope.launch { writeNow(text) }
    }

    /**
     * The text the editor should treat as already sent.
     *
     * The in-flight value if there is one, otherwise what the database holds. See
     * [commitLiveText] for why the in-flight value wins.
     */
    private fun publishedBaseline(): String? =
        pendingCommit.value ?: observation.value
            .let { it as? RoomObservation.Ready }
            ?.room
            ?.live
            ?.textOf(uid)

    /**
     * Records whether the field has focus.
     *
     * Deliberately does *not* publish on losing focus. Blur used to flush the draft, which
     * reintroduced exactly the behaviour [onLiveTextChange] no longer has: putting the phone
     * down would publish an edit the writer had not chosen to publish. Leaving the field is not
     * a decision to publish; pressing "Set status" is.
     */
    fun onEditingChanged(isEditing: Boolean) {
        editing.value = isEditing
    }

    private suspend fun writeNow(text: String) {
        val currentUid = uid ?: return
        runCatching { roomRepository.setLiveText(currentUid, roomId, text) }
            .onSuccess {
                lastWrittenText = text
                pendingCommit.value = null
                showSentConfirmation(text)
            }
            .onFailure { error ->
                Log.w(TAG, "Live text write failed", error)
                // Hand the baseline back so "Set status" goes live again: the write did not
                // happen, and a disabled button would be a second wrong statement about it.
                pendingCommit.value = null
                this@RoomViewModel.error.value = RoomScreenError.Offline
            }
    }

    /**
     * Raises the "status sent" chip, and schedules its own removal.
     *
     * Owned here rather than by the screen so the timer cannot outlive the composition: leaving
     * the room clears the view model, and the job dies with `viewModelScope`. The job is also
     * cancelled and replaced on a second send, so pressing twice restarts the dwell instead of
     * having the first timer cut the second chip short.
     */
    private fun showSentConfirmation(text: String) {
        sentText.value = text
        sentNoticeJob?.cancel()
        sentNoticeJob = viewModelScope.launch {
            delay(SENT_NOTICE_MILLIS)
            sentText.value = null
        }
    }

    fun clearLiveText() {
        draft.value = ""
        sentText.value = null
        pendingWrite?.cancel()
        viewModelScope.launch {
            val currentUid = uid ?: return@launch
            runCatching { roomRepository.clearLiveText(currentUid, roomId) }
                .onSuccess { lastWrittenText = "" }
        }
    }

    // ---------------------------------------------------------------- live update

    /**
     * Carries out a [LiveUpdateDecision] against the ongoing notification.
     *
     * A room with nothing to say is *paused*: no status means no status bar entry. Posting
     * "Nothing yet" would occupy a permanent surface on the user's screen to say nothing, and it
     * would do so the moment a room was opened or a partner left — the two moments least likely
     * to want it. The Live Update therefore appears when there is something to show and is
     * withdrawn when there is not, and the room screen's indicator is the visible counterpart.
     *
     * *What* to show is not decided here. It is [decideLiveUpdate], which is shared with
     * [LiveUpdateService] — the same function that decides while the app is closed and the
     * service is the only thing listening. One rule with two callers is what keeps the chip and
     * this screen telling the same story; two copies of the rule is how they stop agreeing.
     *
     * Called for every room event, whether the value came from this device or the partner, which
     * is what makes "no partner", "partner joined" and "partner cleared it" all behave correctly
     * without any of them needing a special case at the call site.
     */
    private fun onLiveUpdateDecision(decision: LiveUpdateDecision) {
        when (decision) {
            LiveUpdateDecision.Unchanged -> Unit

            is LiveUpdateDecision.Post -> {
                lastNotifiedText = decision.text

                // Dispatched off the main thread: the notification manager is a binder call and
                // there is no reason to spend a frame on it while typing.
                notificationScope.launchSafely("Live Update post") {
                    LiveUpdateService.show(application, roomId, decision.text)
                }
            }

            LiveUpdateDecision.Withdraw -> {
                // Only clear when something was actually up, so a room that is already quiet
                // does not issue a cancellation on every room event.
                if (lastNotifiedText != null) cancelLiveUpdate()
            }
        }
    }

    private fun cancelLiveUpdate() {
        lastNotifiedText = null
        notificationScope.launchSafely("Live Update cancel") {
            LiveUpdateService.cancel(application, roomId)
        }
    }

    // ---------------------------------------------------------------- leaving

    fun requestLeave() {
        leaveDialog.value = true
    }

    fun dismissLeave() {
        leaveDialog.value = false
    }

    fun confirmLeave() {
        if (leaving.value) return
        leaveDialog.value = false
        leaving.value = true
        error.value = null

        viewModelScope.launch {
            val currentUid = uid
            val result = if (currentUid == null) {
                RoomResult.Failure(RoomError.NotAMember)
            } else {
                roomRepository.leaveRoom(currentUid, roomId)
            }
            leaving.value = false
            when (result) {
                is RoomResult.Success -> {
                    preferences.setCurrentRoomId(null)
                    cancelLiveUpdate()
                    // Nothing of ours should remain in the status bar.
                    notificationScope.launchSafely("Live Update cleanup") {
                        LiveUpdateService.cancel(application, roomId)
                    }
                    _events.tryEmit(RoomEvent.LeftRoom(roomId))
                }

                is RoomResult.Failure -> error.value = RoomScreenError.LeaveFailed
            }
        }
    }

    fun dismissError() {
        error.value = null
    }

    override fun onCleared() {
        // The Live Update is meant to outlive the screen: it keeps reflecting the room while
        // Pair is backgrounded. It is cancelled on leave, not on navigation.
        super.onCleared()
    }

    private companion object {
        const val TAG = "RoomViewModel"

        /** Keeps the observation flowing for a moment across configuration changes. */
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /**
         * How long the "status sent" chip stays up.
         *
         * Long enough to read without hunting for it, short enough that it is gone before the
         * user starts wondering whether it is still true. It is deliberately not tied to the
         * notification's own lifetime, which is the partner's business, not this screen's.
         */
        const val SENT_NOTICE_MILLIS = 2_200L
    }
}
