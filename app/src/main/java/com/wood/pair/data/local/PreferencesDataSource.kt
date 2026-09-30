package com.wood.pair.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wood.pair.data.model.RoomId
import com.wood.pair.ui.theme.Appearance
import com.wood.pair.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pair_prefs")

/** Everything Pair persists on the device. */
data class LocalState(
    val displayName: String = "",
    val currentRoomId: String? = null,
    val appearance: Appearance = Appearance(),
    val liveUpdateEnabled: Boolean = true,
    /**
     * Which avatar the user picked, as an [com.wood.pair.ui.components.Avatars.Option.id].
     *
     * Stored as the id rather than a drawable resource id so the drawables can be renamed
     * without invalidating saved choices.
     */
    val avatarId: String? = null,
) {
    val hasCompletedOnboarding: Boolean get() = displayName.isNotBlank()
}

/**
 * Local preferences.
 *
 * Firebase Auth owns the session; this store only remembers the human-readable name, which
 * room the user was last in (so relaunching returns them to it), their chosen avatar, and
 * display preferences. No auth secret is written here.
 */
class PreferencesDataSource(private val context: Context) {

    private object Keys {
        val DisplayName = stringPreferencesKey("display_name")
        val CurrentRoomId = stringPreferencesKey("current_room_id")
        val ThemeMode = stringPreferencesKey("theme_mode")
        val DynamicColor = booleanPreferencesKey("dynamic_color")
        val AmoledDark = booleanPreferencesKey("amoled_dark")
        val LiveUpdateEnabled = booleanPreferencesKey("live_update_enabled")
        val AvatarId = stringPreferencesKey("avatar_id")
    }

    val state: Flow<LocalState> = context.dataStore.data
        // A corrupt/unreadable preferences file must not take the app down.
        .catch { cause ->
            if (cause is IOException) emit(emptyPreferences()) else throw cause
        }
        .map { prefs ->
            LocalState(
                displayName = prefs[Keys.DisplayName].orEmpty(),
                // A stored room ID that no longer matches the rules' shape is discarded
                // rather than trusted, so a bad write can never wedge startup.
                currentRoomId = prefs[Keys.CurrentRoomId]?.takeIf { RoomId.isValid(it) },
                appearance = Appearance(
                    themeMode = ThemeMode.fromName(prefs[Keys.ThemeMode]),
                    dynamicColor = prefs[Keys.DynamicColor] ?: true,
                    amoledDark = prefs[Keys.AmoledDark] ?: true,
                ),
                liveUpdateEnabled = prefs[Keys.LiveUpdateEnabled] ?: true,
                avatarId = prefs[Keys.AvatarId]?.takeIf { it.isNotBlank() },
            )
        }
        .distinctUntilChanged()

    val displayName: Flow<String> = state.map { it.displayName }.distinctUntilChanged()
    val currentRoomId: Flow<String?> = state.map { it.currentRoomId }.distinctUntilChanged()
    val appearance: Flow<Appearance> = state.map { it.appearance }.distinctUntilChanged()
    val liveUpdateEnabled: Flow<Boolean> = state.map { it.liveUpdateEnabled }.distinctUntilChanged()

    suspend fun setDisplayName(name: String) {
        context.dataStore.edit { it[Keys.DisplayName] = name.trim() }
    }

    suspend fun setCurrentRoomId(roomId: String?) {
        context.dataStore.edit { prefs ->
            if (roomId == null) {
                prefs.remove(Keys.CurrentRoomId)
            } else {
                prefs[Keys.CurrentRoomId] = roomId
            }
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.ThemeMode] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DynamicColor] = enabled }
    }

    suspend fun setAmoledDark(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AmoledDark] = enabled }
    }

    suspend fun setLiveUpdateEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LiveUpdateEnabled] = enabled }
    }

    suspend fun setAvatarId(avatarId: String?) {
        context.dataStore.edit { prefs ->
            if (avatarId == null) {
                prefs.remove(Keys.AvatarId)
            } else {
                prefs[Keys.AvatarId] = avatarId
            }
        }
    }

    /** Clears identity-linked state. Used when the account is reset. */
    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.CurrentRoomId)
            prefs.remove(Keys.DisplayName)
        }
    }
}
