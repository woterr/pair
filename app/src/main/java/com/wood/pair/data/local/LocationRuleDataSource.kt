package com.wood.pair.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wood.pair.data.model.LocationRule
import com.wood.pair.data.model.TimeWindow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

private val Context.ruleDataStore: DataStore<Preferences> by preferencesDataStore(name = "pair_rules")

/**
 * Stores location rules locally.
 *
 * Deliberately device-local: a wallpaper rule is personal behaviour tied to one phone, and
 * uploading someone's wallpapers and favourite places to a server would be a needless
 * privacy cost. If cross-device rules are ever wanted, this class is the only thing that
 * needs replacing — the UI talks to it, not to JSON.
 */
class LocationRuleDataSource(context: Context) {

    private val appContext = context.applicationContext
    private object Keys {
        val Rules = stringPreferencesKey("location_rules_json")
    }

    val rules: Flow<List<LocationRule>> = appContext.ruleDataStore.data
        .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
        .map { prefs -> decode(prefs[Keys.Rules].orEmpty()) }

    suspend fun current(): List<LocationRule> = rules.first()

    suspend fun upsert(rule: LocationRule) {
        appContext.ruleDataStore.edit { prefs ->
            val existing = decode(prefs[Keys.Rules].orEmpty()).toMutableList()
            val index = existing.indexOfFirst { it.id == rule.id }
            if (index >= 0) existing[index] = rule else existing.add(rule)
            prefs[Keys.Rules] = encode(existing)
        }
    }

    suspend fun delete(ruleId: String) {
        appContext.ruleDataStore.edit { prefs ->
            val remaining = decode(prefs[Keys.Rules].orEmpty()).filterNot { it.id == ruleId }
            prefs[Keys.Rules] = encode(remaining)
        }
    }

    // ------------------------------------------------------------- serialisation

    private fun encode(rules: List<LocationRule>): String {
        val array = JSONArray()
        rules.forEach { rule ->
            val obj = JSONObject().apply {
                put("id", rule.id)
                put("label", rule.label)
                put("lat", rule.latitude)
                put("lon", rule.longitude)
                put("radius", rule.radiusMeters.toDouble())
                put("wallpaper", rule.wallpaperUri)
                put("active", rule.isActive)
                rule.timeWindow?.let { window ->
                    put(
                        "time",
                        JSONObject().apply {
                            put("start", window.startMinute)
                            put("end", window.endMinute)
                            put("days", JSONArray().apply { window.daysOfWeek.forEach(::put) })
                        },
                    )
                }
            }
            array.put(obj)
        }
        return array.toString()
    }

    /**
     * Reads rules back, skipping any entry that cannot be understood.
     *
     * A single malformed record must not take down the whole list, and it must never crash
     * the app on launch.
     */
    private fun decode(json: String): List<LocationRule> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val rule = obj.toRule() ?: continue
                    add(rule)
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun JSONObject.toRule(): LocationRule? = runCatching {
        val id = optString("id").takeIf { it.isNotBlank() } ?: return null
        val wallpaper = optString("wallpaper").takeIf { it.isNotBlank() } ?: return null
        val radius = optDouble("radius", Double.NaN).toFloat()
        val radiusMeters = if (radius.isNaN()) {
            LocationRule.DEFAULT_RADIUS_METERS
        } else {
            radius.coerceIn(LocationRule.MIN_RADIUS_METERS, LocationRule.MAX_RADIUS_METERS)
        }
        LocationRule(
            id = id,
            label = optString("label"),
            latitude = optDouble("lat", Double.NaN),
            longitude = optDouble("lon", Double.NaN),
            radiusMeters = radiusMeters,
            timeWindow = optJSONObject("time")?.toTimeWindow(),
            wallpaperUri = wallpaper,
            isActive = optBoolean("active", true),
        )
    }.getOrNull()

    private fun JSONObject.toTimeWindow(): TimeWindow? = runCatching {
        val daysArray = optJSONArray("days") ?: JSONArray()
        val days = buildSet { for (i in 0 until daysArray.length()) add(daysArray.optInt(i)) }
        TimeWindow(
            startMinute = optInt("start").coerceIn(0, TimeWindow.MINUTES_PER_DAY),
            endMinute = optInt("end").coerceIn(0, TimeWindow.MINUTES_PER_DAY),
            daysOfWeek = days.filter { it in 1..7 }.toSet(),
        )
    }.getOrNull()
}
