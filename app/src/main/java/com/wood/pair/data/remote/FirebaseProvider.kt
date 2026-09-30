package com.wood.pair.data.remote

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import com.wood.pair.R

/**
 * Single place where the Firebase SDK objects are obtained.
 *
 * Composable code never touches these; repositories and services do.
 *
 * The default [FirebaseApp] is initialised by the `com.google.gms.google-services` Gradle
 * plugin from `app/google-services.json`. That file contains only *public* client
 * configuration (project id, API key, app id, sender id) — the same values that ship in any
 * consumer Firebase app. There is no server key, no service account and no Admin SDK
 * credential in this APK; sending is the Cloud Function's job alone.
 */
object FirebaseProvider {

    private const val TAG = "PairFirebase"

    /**
     * Why the database URL can be missing, and exactly how to fix it.
     *
     * A `google-services.json` downloaded before the Realtime Database was created has no
     * database URL. The parameterless lookup then silently resolves to a host that does not
     * exist: the app reports "Offline" and room creation appears to hang with no error at
     * all. Saying so plainly is the whole point of this guard.
     */
    private const val MISSING_DATABASE_URL =
        "No Realtime Database URL in the Firebase configuration, so the database can never " +
            "connect. Re-download app/google-services.json from the Firebase console - the " +
            "database now exists, so the URL will be included - or add a firebase_url entry " +
            "under project_info."

    /** The app's primary Firebase instance. */
    val app: FirebaseApp
        get() = FirebaseApp.getInstance()

    val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance(app)

    /**
     * The database root reference.
     *
     * Note there is no `FirebaseDatabase.root` property in the current SDK; `getReference()`
     * with no path is the root.
     */
    val database: FirebaseDatabase
        get() {
            val url = app.options.databaseUrl
            check(!url.isNullOrBlank()) { MISSING_DATABASE_URL }
            return FirebaseDatabase.getInstance(app, url)
        }

    val messaging: FirebaseMessaging
        get() = FirebaseMessaging.getInstance()

    /**
     * Creates an additional, independently named [FirebaseApp] pointing at the same Firebase
     * project.
     *
     * This exists solely for the debug-only "second device" harness (see
     * `com.wood.pair.debug.PeerDevice`). A second app under a different name gets its own
     * anonymous Auth account and its own FCM registration, so it behaves like a genuinely
     * separate installation rather than a mock. Release builds never call it.
     */
    fun createNamedInstance(context: Context, name: String): FirebaseApp {
        FirebaseApp.getApps(context).firstOrNull { it.name == name }?.let { return it }
        val created = FirebaseApp.initializeApp(context, publicOptions(context), name)
        Log.i(TAG, "Created secondary FirebaseApp '$name'")
        return created
    }

    /**
     * The public client configuration, rebuilt from the string resources the
     * google-services plugin generates from `app/google-services.json`.
     *
     * These are public client values, not secrets.
     */
    fun publicOptions(context: Context): FirebaseOptions = FirebaseOptions.Builder()
        .setApplicationId(context.getString(R.string.google_app_id))
        .setApiKey(context.getString(R.string.google_api_key))
        .setProjectId(context.getString(R.string.project_id))
        .setGcmSenderId(context.getString(R.string.gcm_defaultSenderId))
        .build()
}
