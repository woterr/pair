package com.wood.pair.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.wood.pair.PairApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Re-registers geofences after a reboot or an app update.
 *
 * Play Services drops registered geofences when the device restarts, so without this the
 * user's rules would silently stop firing. Re-registering from storage is safe to do
 * repeatedly, because [GeofenceRegistrar.sync] replaces the whole set rather than adding to it.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val pendingResult = goAsync()
        val application = context.applicationContext as? PairApplication
        if (application == null) {
            Log.w(TAG, "Application is not PairApplication; nothing to re-register")
            pendingResult.finish()
            return
        }

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val graph = application.graph
                val outcome = graph.geofenceRegistrar.sync(graph.locationRules.current())
                Log.i(TAG, "Geofences re-registered after $action: $outcome")
            } catch (error: Throwable) {
                Log.e(TAG, "Could not re-register geofences after $action", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
