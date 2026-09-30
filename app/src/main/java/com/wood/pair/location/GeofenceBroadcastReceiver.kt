package com.wood.pair.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.wood.pair.PairApplication
import com.wood.pair.data.model.LocationRule
import com.wood.pair.wallpaper.WallpaperResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Receives geofence transitions and applies the matching wallpaper.
 *
 * This is where the second feature chain completes:
 * `geofence -> ENTER -> rule matches -> wallpaper set`.
 *
 * A [BroadcastReceiver] has roughly ten seconds before the system may kill it, so the work
 * is done through [android.content.BroadcastReceiver.goAsync] and the result is always
 * reported. Doing it inline would risk being cut off mid-write.
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent)
        if (event == null) {
            Log.w(TAG, "Received a geofence broadcast that could not be parsed")
            return
        }
        if (event.hasError()) {
            // A registration error affects every geofence; re-syncing is the only useful
            // response, and it is logged rather than silently dropped.
            Log.w(TAG, "Geofencing error code ${event.errorCode}")
            return
        }

        if (event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            handleEnter(context, event.triggeringGeofences)
        }
        // EXIT is intentionally not used to restore anything: reverting a wallpaper would be
        // guesswork without a saved copy of the previous one, and the user did not ask for it.
    }

    private fun handleEnter(context: Context, geofences: List<Geofence>?) {
        val ids = geofences?.map { it.requestId }.orEmpty()
        if (ids.isEmpty()) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val application = appContext as? PairApplication
        if (application == null) {
            Log.w(TAG, "Application is not PairApplication; ignoring")
            pendingResult.finish()
            return
        }

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val graph = application.graph
                val rules = graph.locationRules.current().filter { it.id in ids }
                val now = Calendar.getInstance()
                val minuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
                val dayOfWeek = now.get(Calendar.DAY_OF_WEEK)

                rules.forEach { rule ->
                    if (!rule.isActive) return@forEach
                    if (rule.timeWindow?.contains(minuteOfDay, dayOfWeek) == false) {
                        Log.i(TAG, "Rule ${rule.id} matched a place but its time window excluded it")
                        return@forEach
                    }
                    when (val outcome = graph.wallpaperStore.applySystem(rule.wallpaperUri)) {
                        is WallpaperResult.Applied ->
                            Log.i(TAG, "Applied wallpaper for rule ${rule.id} (${rule.label})")

                        is WallpaperResult.Rejected ->
                            Log.w(TAG, "Rule ${rule.id} wallpaper rejected: ${outcome.reason}")
                    }
                }
            } catch (error: Throwable) {
                Log.e(TAG, "Failed to handle geofence entry", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "GeofenceReceiver"
    }
}

/** Convenience for the rule check, kept here so the receiver stays readable. */
internal fun LocationRule.isApplicableAt(minuteOfDay: Int, dayOfWeek: Int): Boolean =
    isActive && (timeWindow == null || timeWindow.contains(minuteOfDay, dayOfWeek))
