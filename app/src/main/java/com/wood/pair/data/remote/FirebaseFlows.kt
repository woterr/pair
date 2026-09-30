package com.wood.pair.data.remote

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.MutableData
import com.google.firebase.database.Query
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

/** Raised when a Firebase operation exceeds the time the UI is willing to wait. */
class FirebaseOperationTimeoutException(message: String) : Exception(message)

/** Default deadline applied to every user-facing Firebase operation. */
const val DEFAULT_TIMEOUT_MILLIS: Long = 15_000

/**
 * Suspending bridge for [com.google.android.gms.tasks.Task].
 *
 * A [CancellationException] is rethrown rather than absorbed: when the coroutine is cancelled
 * the Task completes with that exception, and swallowing it would break structured
 * concurrency. `resumeWith` is used instead of `resume` because `CancellableContinuation`'s
 * `resume` requires an explicit `onCancellation` handler in current kotlinx.coroutines.
 */
suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
    suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            if (!cont.isActive) return@addOnCompleteListener
            val error = task.exception
            when {
                error is CancellationException -> cont.cancel(error)
                error != null -> cont.resumeWith(Result.failure(error))
                else -> @Suppress("UNCHECKED_CAST") cont.resumeWith(Result.success(task.result as T))
            }
        }
    }

/** Best-effort write for cleanup paths, where a failure must not mask the real outcome. */
suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitQuietly() {
    runCatching { await() }
}

/**
 * Awaits a `Task<Void>` and discards its result.
 *
 * Deliberately non-generic: several call sites use a task purely for its completion, and a
 * `Void` payload makes type inference unreliable in statement position.
 */
suspend fun com.google.android.gms.tasks.Task<Void>.awaitUnit() {
    suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            if (!cont.isActive) return@addOnCompleteListener
            val error = task.exception
            when {
                error is CancellationException -> cont.cancel(error)
                error != null -> cont.resumeWith(Result.failure(error))
                else -> cont.resumeWith(Result.success(Unit))
            }
        }
    }
}

/**
 * Bridges [DatabaseReference.runTransaction] to a suspending call.
 *
 * `runTransaction` returns `void` in the current SDK — completion is reported only through
 * [Transaction.Handler.onComplete] — so the coroutine is resumed from that callback. The
 * boolean is the `committed` flag.
 */
suspend fun DatabaseReference.awaitTransaction(handler: Transaction.Handler): Boolean =
    suspendCancellableCoroutine { cont ->
        val proxy = object : Transaction.Handler {
            override fun doTransaction(current: MutableData): Transaction.Result =
                handler.doTransaction(current)

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                current: DataSnapshot?,
            ) {
                // Let the caller's own handler record its outcome first.
                handler.onComplete(error, committed, current)
                if (cont.isActive) cont.resumeWith(Result.success(committed))
            }
        }
        runTransaction(proxy)
    }

/**
 * True when a [MutableData] node holds no value.
 *
 * `MutableData` has no `exists()` (only `DataSnapshot` does), so presence is determined by the
 * value. A node explicitly set to `false` or `0` is present, which is exactly the distinction
 * the room slot hint depends on.
 */
fun MutableData.isAbsent(): Boolean = value == null

/**
 * Bridges a Firebase [ValueEventListener] to a [Flow].
 *
 * Two properties matter for this app:
 *
 * 1. A `ValueEventListener` fires immediately with the current value — or with "this node
 *    does not exist" — before any network round trip. So the flow always produces a first
 *    value, and a missing node is an ordinary value rather than a reason to keep waiting.
 *    This is the structural reason the UI cannot get stuck on a spinner.
 * 2. The listener is always detached in `awaitClose`, so cancelling a collector really does
 *    unsubscribe. [conflate] stops a slow link from building a backlog of stale values.
 */
fun Query.snapshotFlow(): Flow<DataSnapshot> = callbackFlow {
    val listener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            trySend(snapshot)
        }

        override fun onCancelled(error: DatabaseError) {
            // Surface the failure instead of leaving a silently dead flow, which would be
            // indistinguishable from "still loading".
            close(error.toException())
        }
    }
    addValueEventListener(listener)
    awaitClose { removeEventListener(listener) }
}.conflate().flowOn(Dispatchers.IO)

/**
 * Runs a Firebase operation with a hard deadline.
 *
 * Every user-facing Firebase call in Pair is wrapped in this. Firebase can leave an
 * operation pending indefinitely on a half-open connection; without a deadline the UI sits
 * on a spinner forever with no way out. With one, the user gets a real error and a retry.
 */
suspend fun <T> firebaseWithTimeout(
    what: String,
    timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    block: suspend () -> T,
): T = try {
    withTimeout(timeoutMillis) { block() }
} catch (e: TimeoutCancellationException) {
    throw FirebaseOperationTimeoutException("Timed out waiting for $what")
}

// ------------------------------------------------------------- snapshot helpers

/** True when this node does not exist. */
fun DataSnapshot.isMissing(): Boolean = !exists()

/** Reads a child as a nullable string, tolerating absence. */
fun DataSnapshot.childStringOrNull(name: String): String? =
    child(name).takeUnless { it.isMissing() }?.getValue(String::class.java)

/** Reads a child of a child as a nullable string, tolerating absence. */
fun DataSnapshot.childStringOrNull(parent: String, name: String): String? =
    child(parent).childStringOrNull(name)

/** First snapshot, or `null` if the flow produced nothing. */
suspend fun Flow<DataSnapshot>.firstSnapshotOrNull(): DataSnapshot? = firstOrNull()

/** True when the first snapshot reports an existing node. */
suspend fun Flow<DataSnapshot>.firstExists(): Boolean = first().exists()
