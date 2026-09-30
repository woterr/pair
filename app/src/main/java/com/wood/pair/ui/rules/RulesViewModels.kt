package com.wood.pair.ui.rules

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wood.pair.data.local.LocationRuleDataSource
import com.wood.pair.data.model.LocationRule
import com.wood.pair.data.model.TimeWindow
import com.wood.pair.location.GeofenceError
import com.wood.pair.location.GeofenceRegistrar
import com.wood.pair.location.GeofenceResult
import com.wood.pair.location.LocationRulePicker
import com.wood.pair.wallpaper.WallpaperResult
import com.wood.pair.wallpaper.WallpaperStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.wood.pair.data.model.WallpaperTarget

/** State of the list of location rules. */
data class RulesUiState(
    val rules: List<LocationRule> = emptyList(),
    val isLoading: Boolean = true,
    val armedCount: Int = 0,
    /** The signed-in user's own name and avatar, for the shared top bar. */
    val displayName: String = "",
    val avatarId: String? = null,
    val currentRoomId: String? = null,
)

class RulesViewModel(
    private val ruleDataSource: LocationRuleDataSource,
    private val geofenceRegistrar: GeofenceRegistrar,
    private val wallpaperStore: WallpaperStore,
    private val preferences: com.wood.pair.data.local.PreferencesDataSource,
) : ViewModel() {

    private val armed = MutableStateFlow(0)

    val uiState: StateFlow<RulesUiState> = combine(
        ruleDataSource.rules,
        armed,
        preferences.state,
    ) { rules, armedCount, local ->
        RulesUiState(
            rules = rules,
            isLoading = false,
            armedCount = armedCount,
            displayName = local.displayName,
            avatarId = local.avatarId,
            currentRoomId = local.currentRoomId,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RulesUiState(),
    )

    init {
        // Re-sync whenever the stored rules change, so the system's geofence list can never
        // drift away from what the user has configured.
        viewModelScope.launch {
            ruleDataSource.rules.collect { syncGeofences(it) }
        }
    }

    fun setActive(rule: LocationRule, isActive: Boolean) {
        viewModelScope.launch { ruleDataSource.upsert(rule.copy(isActive = isActive)) }
    }

    fun delete(rule: LocationRule) {
        viewModelScope.launch {
            ruleDataSource.delete(rule.id)
            // The image is only referenced by this rule, so it can go too.
            wallpaperStore.discard(rule.wallpaperUri)
        }
    }

    private suspend fun syncGeofences(rules: List<LocationRule>) {
        when (val result = geofenceRegistrar.sync(rules)) {
            is GeofenceResult.Applied -> armed.value = result.armed
            is GeofenceResult.Rejected -> armed.value = 0
        }
    }
}

/** State of the create/edit rule screen. */
data class RuleEditorUiState(
    val ruleId: String = "",
    val label: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val placeName: String = "",
    val radiusMeters: Float = LocationRule.DEFAULT_RADIUS_METERS,
    val hasTimeRestriction: Boolean = false,
    val startMinute: Int = 8 * 60,
    val endMinute: Int = 18 * 60,
    val daysOfWeek: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
    val wallpaperReference: String = "",
    /** Which surfaces this rule will change. See [WallpaperTarget]. */
    val wallpaperTarget: WallpaperTarget = WallpaperTarget.Home,
    val isSaving: Boolean = false,
    val isLoading: Boolean = false,
    val error: RuleEditorError? = null,
    /** The signed-in user's own name and avatar, for the shared top bar. */
    val displayName: String = "",
    val avatarId: String? = null,
    val currentRoomId: String? = null,
) {
    val hasLocation: Boolean get() = latitude != null && longitude != null
    val hasWallpaper: Boolean get() = wallpaperReference.isNotBlank()
    val canSave: Boolean get() = hasLocation && hasWallpaper && !isSaving
}

enum class RuleEditorError {
    LocationRequired,
    LocationUnavailable,
    WallpaperRequired,
    GeofencePermissionMissing,
    GeofenceFailed,
    WallpaperFailed,
}

class RuleEditorViewModel(
    private val application: Application,
    private val ruleDataSource: LocationRuleDataSource,
    private val geofenceRegistrar: GeofenceRegistrar,
    private val wallpaperStore: WallpaperStore,
    private val locationPicker: LocationRulePicker,
    private val preferences: com.wood.pair.data.local.PreferencesDataSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RuleEditorUiState())
    val uiState: StateFlow<RuleEditorUiState> = _uiState.asStateFlow()

    init {
        // The top bar is shared chrome, so the identity it shows is read straight from the same
        // local store the settings screen writes to.
        viewModelScope.launch {
            preferences.state.collect { local ->
                _uiState.value = _uiState.value.copy(
                    displayName = local.displayName,
                    avatarId = local.avatarId,
                    currentRoomId = local.currentRoomId,
                )
            }
        }
    }

    /** Loads an existing rule for editing. A null id starts a new one. */
    fun load(ruleId: String?) {
        viewModelScope.launch {
            val current = _uiState.value
            if (ruleId == null) {
                _uiState.value = RuleEditorUiState(
                    displayName = current.displayName,
                    avatarId = current.avatarId,
                    currentRoomId = current.currentRoomId,
                )
                return@launch
            }
            _uiState.value = current.copy(isLoading = true)
            val existing = ruleDataSource.current().firstOrNull { it.id == ruleId }
            _uiState.value = if (existing == null) {
                RuleEditorUiState(
                    displayName = current.displayName,
                    avatarId = current.avatarId,
                    currentRoomId = current.currentRoomId,
                )
            } else {
                RuleEditorUiState(
                    ruleId = existing.id,
                    label = existing.label,
                    latitude = existing.latitude,
                    longitude = existing.longitude,
                    placeName = existing.label,
                    radiusMeters = existing.radiusMeters,
                    hasTimeRestriction = existing.timeWindow != null,
                    startMinute = existing.timeWindow?.startMinute ?: 8 * 60,
                    endMinute = existing.timeWindow?.endMinute ?: 18 * 60,
                    daysOfWeek = existing.timeWindow?.daysOfWeek
                        ?: setOf(1, 2, 3, 4, 5, 6, 7),
                    wallpaperReference = existing.wallpaperUri,
                    wallpaperTarget = existing.wallpaperTarget,
                    displayName = current.displayName,
                    avatarId = current.avatarId,
                    currentRoomId = current.currentRoomId,
                )
            }
        }
    }

    fun onLabelChange(value: String) {
        _uiState.value = _uiState.value.copy(label = value, error = null)
    }

    fun onLocationPicked(latitude: Double, longitude: Double, placeName: String) {
        _uiState.value = _uiState.value.copy(
            latitude = latitude,
            longitude = longitude,
            placeName = placeName,
            error = null,
        )
    }

    fun onRadiusChange(radius: Float) {
        _uiState.value = _uiState.value.copy(radiusMeters = radius)
    }

    /**
     * Uses the device's current position as the rule's location.
     *
     * Goes through [LocationRulePicker], which takes a single one-shot fix rather than running
     * a continuous subscription.
     */
    fun useCurrentLocation() {
        viewModelScope.launch {
            locationPicker.currentLocation()
                .onSuccess { picked ->
                    _uiState.value = _uiState.value.copy(
                        latitude = picked.latitude,
                        longitude = picked.longitude,
                        placeName = picked.label,
                        label = _uiState.value.label.ifBlank { picked.label },
                        error = null,
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        error = RuleEditorError.LocationUnavailable,
                    )
                }
        }
    }

    fun onTimeRestrictionChange(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(hasTimeRestriction = enabled)
    }

    fun onStartMinuteChange(minute: Int) {
        _uiState.value = _uiState.value.copy(startMinute = minute.coerceIn(0, TimeWindow.MINUTES_PER_DAY))
    }

    fun onEndMinuteChange(minute: Int) {
        _uiState.value = _uiState.value.copy(endMinute = minute.coerceIn(0, TimeWindow.MINUTES_PER_DAY))
    }

    fun toggleDay(day: Int) {
        _uiState.value = _uiState.value.let { state ->
            val days = state.daysOfWeek.toMutableSet()
            if (!days.remove(day)) days.add(day)
            state.copy(daysOfWeek = days)
        }
    }

    /**
     * Copies a chosen image into app storage.
     *
     * Local storage keeps the rule working after the original photo is deleted, and avoids
     * holding a content-URI permission or uploading anything personal.
     */
    /**
     * Records which surfaces this rule should change.
     *
     * Takes effect on save, like every other field here, so the screen never shows a combination
     * the stored rule does not actually have.
     */
    fun onWallpaperTargetChange(target: WallpaperTarget) {
        _uiState.value = _uiState.value.copy(wallpaperTarget = target, error = null)
    }

    fun onWallpaperPicked(uri: Uri) {
        viewModelScope.launch {
            val reference = wallpaperStore.importImage(uri)
            if (reference == null) {
                _uiState.value = _uiState.value.copy(error = RuleEditorError.WallpaperFailed)
            } else {
                _uiState.value = _uiState.value.copy(wallpaperReference = reference, error = null)
            }
        }
    }

    fun save(onSaved: () -> Unit) {
        val state = _uiState.value
        if (!state.hasLocation) {
            _uiState.value = state.copy(error = RuleEditorError.LocationRequired)
            return
        }
        if (!state.hasWallpaper) {
            _uiState.value = state.copy(error = RuleEditorError.WallpaperRequired)
            return
        }

        _uiState.value = state.copy(isSaving = true, error = null)
        viewModelScope.launch {
            val rule = LocationRule(
                id = state.ruleId.ifBlank { LocationRule.newId() },
                label = state.label.ifBlank { state.placeName },
                latitude = state.latitude!!,
                longitude = state.longitude!!,
                radiusMeters = state.radiusMeters,
                timeWindow = if (state.hasTimeRestriction) {
                    TimeWindow(
                        startMinute = state.startMinute,
                        endMinute = state.endMinute,
                        daysOfWeek = state.daysOfWeek,
                    )
                } else {
                    null
                },
                wallpaperUri = state.wallpaperReference,
                wallpaperTarget = state.wallpaperTarget,
                isActive = true,
            )
            ruleDataSource.upsert(rule)
            _uiState.value = _uiState.value.copy(isSaving = false)
            onSaved()
        }
    }

    /** Applies the chosen wallpaper immediately, so the user can confirm the result. */
    fun previewWallpaper(onResult: (WallpaperResult) -> Unit) {
        val reference = _uiState.value.wallpaperReference
        if (reference.isBlank()) return
        viewModelScope.launch {
            // The chosen surface, not a hard-coded one: previewing "Lock screen" and then
            // watching the home screen change would tell the user the opposite of what they
            // asked to see.
            onResult(wallpaperStore.apply(reference, _uiState.value.wallpaperTarget))
        }
    }

    fun hasLocationPermission(): Boolean = geofenceRegistrar.hasLocationPermission()

    fun reportGeofenceResult(result: GeofenceResult) {
        val error = when (result) {
            is GeofenceResult.Rejected -> when (val reason = result.error) {
                GeofenceError.PermissionMissing -> RuleEditorError.GeofencePermissionMissing
                is GeofenceError.Failed -> RuleEditorError.GeofenceFailed
            }

            is GeofenceResult.Applied -> null
        }
        if (error != null) _uiState.value = _uiState.value.copy(error = error)
    }
}
