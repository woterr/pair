package com.wood.pair.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.wood.pair.data.model.LocationRule
import com.wood.pair.data.remote.awaitUnit

/** Why a geofence operation failed, in terms the UI can act on. */
sealed interface GeofenceError {
    /** The user has not granted foreground location access. */
    data object PermissionMissing : GeofenceError

    data class Failed(val message: String) : GeofenceError
}

sealed interface GeofenceResult {
    data class Applied(val armed: Int) : GeofenceResult
    data class Rejected(val error: GeofenceError) : GeofenceResult
}

/**
 * Registers location rules as system geofences.
 *
 * Pair never polls GPS. Rules are handed to Play Services once and the system wakes Pair
 * through a broadcast when a boundary is crossed, which is both battery-honest and the only
 * approach that still works after the app has been killed.
 *
 * The current `play-services-location` API registers geofences against a [PendingIntent]
 * rather than a bare broadcast receiver, and removal is likewise keyed on either that intent
 * or a list of request IDs.
 */
class GeofenceRegistrar(context: Context) {

    private val appContext = context.applicationContext
    private val client: GeofencingClient by lazy {
        LocationServices.getGeofencingClient(appContext)
    }

    /**
     * The intent the system uses to deliver geofence transitions.
     *
     * It must be **mutable**: Play Services fills in the triggering geofences and transition
     * as extras, and an immutable PendingIntent would reject them.
     */
    private val geofenceIntent: PendingIntent by lazy {
        val intent = Intent(appContext, GeofenceBroadcastReceiver::class.java)
        PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    /**
     * True when the app holds the foreground location permission needed to register a
     * geofence at all.
     */
    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Replaces the registered geofence set with the currently active rules.
     *
     * A full replace rather than a diff keeps the system's view exactly in step with storage,
     * which matters after a reboot or an app update when the system's list may be stale.
     */
    @SuppressLint("MissingPermission")
    suspend fun sync(rules: List<LocationRule>): GeofenceResult {
        if (!hasLocationPermission()) {
            return GeofenceResult.Rejected(GeofenceError.PermissionMissing)
        }

        val armable = rules.filter { it.isActive }
        removeAll()

        if (armable.isEmpty()) return GeofenceResult.Applied(0)

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(Geofence.GEOFENCE_TRANSITION_ENTER)
            .addGeofences(armable.map(::toGeofence))
            .build()

        return runCatching {
            // Suspended to completion, so the caller knows whether registration actually
            // succeeded instead of assuming it did.
            client.addGeofences(request, geofenceIntent).awaitUnit()
            GeofenceResult.Applied(armable.size)
        }.getOrElse { error ->
            Log.w(TAG, "Geofence registration failed", error)
            GeofenceResult.Rejected(GeofenceError.Failed(error.message ?: "Unknown error"))
        }
    }

    /** Removes every geofence Pair has registered. */
    suspend fun removeAll() {
        runCatching { client.removeGeofences(geofenceIntent).awaitUnit() }
            .onFailure { Log.w(TAG, "Could not remove geofences", it) }
    }

    private fun toGeofence(rule: LocationRule): Geofence = Geofence.Builder()
        .setRequestId(rule.id)
        .setCircularRegion(rule.latitude, rule.longitude, rule.radiusMeters)
        .setExpirationDuration(Geofence.NEVER_EXPIRE)
        .setTransitionTypes(
            Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT,
        )
        .setLoiteringDelay(LOITERING_DELAY_MILLIS)
        .setNotificationResponsiveness(RESPONSIVENESS_MILLIS)
        .build()

    private companion object {
        const val TAG = "GeofenceRegistrar"
        const val REQUEST_CODE = 7311
        const val LOITERING_DELAY_MILLIS = 60_000
        const val RESPONSIVENESS_MILLIS = 30_000
    }
}
