package com.wood.pair

import android.app.Application

/**
 * The application used by JVM tests.
 *
 * The real [PairApplication] initialises Firebase and the debug peer harness, none of which
 * exist in a Robolectric environment — it fails on the first `FirebaseApp` call. Screens are
 * rendered from plain state, so the tests need an application that starts up and stops there.
 */
class TestApplication : Application()
