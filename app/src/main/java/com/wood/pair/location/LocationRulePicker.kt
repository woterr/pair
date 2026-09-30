package com.wood.pair.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.wood.pair.data.remote.await
import java.util.Locale

/** A single fix, with a human-readable name when the platform can supply one. */
data class PickedLocation(
    val latitude: Double,
    val longitude: Double,
    val label: String,
)

/** Why a location could not be obtained. */
sealed interface PickLocationError {
    data object PermissionMissing : PickLocationError
    data object Unavailable : PickLocationError
}

/**
 * Obtains the device's current position for a location rule.
 *
 * Deliberately uses a single current-position request rather than a continuous fix: the user
 * is standing at the place they are describing, one reading is enough, and a one-shot request
 * avoids leaving location hardware running.
 *
 * The place name comes from the platform [Geocoder]. Reverse geocoding is best-effort — it can
 * be slow or unavailable — so a failure falls back to coordinates instead of failing the flow.
 */
class LocationRulePicker(context: Context) {

    private val appContext = context.applicationContext
    private val client: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(appContext)
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): Result<PickedLocation> {
        if (!hasPermission()) return Result.failure(PickLocationPermissionException)

        val request = CurrentLocationRequest.Builder()
            // PRIORITY_BALANCED_POWER_ACCURACY is the right trade-off here: a circle of a few
            // hundred metres does not need GPS-grade precision, and it costs far less battery.
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setMaxUpdateAgeMillis(MAX_CACHED_AGE_MILLIS)
            .build()

        // The CancellationToken is optional: the request is short-lived, and Pair cancels the
        // surrounding coroutine if the user leaves the screen, which cancels the Task too.
        val location = runCatching { client.getCurrentLocation(request, null).await() }
            .getOrNull()
            ?: return Result.failure(PickLocationUnavailableException)

        val label = runCatching {
            Geocoder(appContext, Locale.getDefault())
                .getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull()
                ?.getAddressLine(0)
        }.onFailure { Log.d(TAG, "Reverse geocoding unavailable", it) }
            .getOrNull()
            .orEmpty()

        return Result.success(
            PickedLocation(
                latitude = location.latitude,
                longitude = location.longitude,
                label = label,
            ),
        )
    }

    private companion object {
        const val TAG = "LocationRulePicker"

        /** Reusing a recent fix avoids a cold GPS start for a place the user is already at. */
        const val MAX_CACHED_AGE_MILLIS = 2 * 60 * 1000L
    }
}

private object PickLocationPermissionException : Exception("Location permission is not granted")
private object PickLocationUnavailableException : Exception("No current location is available")
