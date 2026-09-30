package com.wood.pair.data

import android.content.Context
import com.wood.pair.data.local.LocationRuleDataSource
import com.wood.pair.data.local.PreferencesDataSource
import com.wood.pair.data.remote.FirebaseProvider
import com.wood.pair.data.repository.AuthRepository
import com.wood.pair.data.repository.FcmTokenRepository
import com.wood.pair.data.repository.RoomRepository
import com.wood.pair.location.GeofenceRegistrar
import com.wood.pair.location.LocationRulePicker
import com.wood.pair.wallpaper.WallpaperStore

/**
 * Manual dependency graph.
 *
 * Everything is a lazy singleton created from the application [Context]. That is all the
 * wiring this project needs — the object graph is small, and a DI framework would add
 * build time and indirection without buying anything.
 */
class PairGraph(context: Context) {

    private val appContext: Context = context.applicationContext

    val preferences: PreferencesDataSource by lazy { PreferencesDataSource(appContext) }
    val locationRules: LocationRuleDataSource by lazy { LocationRuleDataSource(appContext) }

    val authRepository: AuthRepository by lazy { AuthRepository(FirebaseProvider.auth) }
    val roomRepository: RoomRepository by lazy { RoomRepository(FirebaseProvider.database) }
    val fcmTokenRepository: FcmTokenRepository by lazy {
        FcmTokenRepository(
            messaging = FirebaseProvider.messaging,
            database = FirebaseProvider.database,
            auth = FirebaseProvider.auth,
        )
    }

    val wallpaperStore: WallpaperStore by lazy { WallpaperStore(appContext) }

    val geofenceRegistrar: GeofenceRegistrar by lazy { GeofenceRegistrar(appContext) }

    val locationRulePicker: LocationRulePicker by lazy { LocationRulePicker(appContext) }
}
