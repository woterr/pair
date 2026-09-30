package com.wood.pair.core

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val TAG = "Pair"

/**
 * Launches detached work that is allowed to fail.
 *
 * A bare `scope.launch { ... }` on a [kotlinx.coroutines.SupervisorJob] still routes an
 * uncaught exception to the thread's default handler, which **crashes the process** — the
 * supervisor only isolates sibling coroutines, it does not swallow failures. Anything running
 * outside a ViewModel's own error handling (notification updates, token registration) must
 * therefore be launched through this.
 */
fun CoroutineScope.launchSafely(
    what: String,
    block: suspend CoroutineScope.() -> Unit,
): Job = launch {
    runCatching { block() }.onFailure { error ->
        Log.w(TAG, "$what failed", error)
    }
}
