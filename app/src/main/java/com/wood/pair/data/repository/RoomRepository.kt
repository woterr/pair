package com.wood.pair.data.repository

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import com.wood.pair.data.model.ConnectionState
import com.wood.pair.data.model.LiveTexts
import com.wood.pair.data.model.Room
import com.wood.pair.data.model.RoomId
import com.wood.pair.data.remote.FirebaseProvider
import com.wood.pair.data.remote.await
import com.wood.pair.data.remote.awaitQuietly
import com.wood.pair.data.remote.awaitTransaction
import com.wood.pair.data.remote.childStringOrNull
import com.wood.pair.data.remote.firebaseWithTimeout
import com.wood.pair.data.remote.firstExists
import com.wood.pair.data.remote.firstSnapshotOrNull
import com.wood.pair.data.remote.isAbsent
import com.wood.pair.data.remote.snapshotFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicReference

/** Paths in the Realtime Database, collected in one place so client and rules stay in step. */
object Paths {
    const val ROOMS = "rooms"
    const val ROOM_SLOTS = "roomSlots"
    const val USERS = "users"

    const val OWNER = "owner"
    const val MEMBER = "member"
    const val LIVE = "live"

    const val UID = "uid"
    const val NAME = "name"

    fun room(roomId: String) = "$ROOMS/$roomId"
    fun slot(roomId: String) = "$ROOM_SLOTS/$roomId"
    fun user(uid: String) = "$USERS/$uid"
}

/** Why a room operation could not complete. Each maps to a distinct, honest UI message. */
sealed interface RoomError {
    /** The room ID does not exist, or the room was dissolved. */
    data object NotFound : RoomError

    /** The room already has two members. */
    data object Full : RoomError

    /** The typed ID is not a legal room ID. */
    data class InvalidId(val input: String) : RoomError

    /** This device is not (or is no longer) a member of the room. */
    data object NotAMember : RoomError

    /** Firebase Auth failed, or the rules refused the write. */
    data class Auth(val message: String) : RoomError

    /** The device could not reach the database. */
    data class Offline(val message: String) : RoomError

    /** The operation exceeded its deadline. */
    data object TimedOut : RoomError

    /** Anything else, with the original message preserved rather than swallowed. */
    data class Unknown(val message: String) : RoomError
}

/** Result of a room mutation. */
sealed interface RoomResult<out T> {
    data class Success<T>(val value: T) : RoomResult<T>
    data class Failure(val error: RoomError) : RoomResult<Nothing>
}

inline fun <T, R> RoomResult<T>.map(transform: (T) -> R): RoomResult<R> = when (this) {
    is RoomResult.Success -> RoomResult.Success(transform(value))
    is RoomResult.Failure -> this
}

/** The state of observing a room. Always terminal — never an indefinite "loading". */
sealed interface RoomObservation {
    /** Reading the room for the first time. Bounded by the observer itself. */
    data object Loading : RoomObservation

    /** The room is gone, dissolved, or this device is no longer a member. */
    data object Unavailable : RoomObservation

    data class Ready(val room: Room) : RoomObservation
}

/**
 * Owns room lifecycle and the room's single live-text value.
 *
 * Data layout and why
 * -------------------
 * ```
 * roomSlots/<roomId>          -> true (slot taken) | false (open)   [public to signed-in]
 * rooms/<roomId>/owner/{uid,name}
 * rooms/<roomId>/member/{uid,name}   (absent while the second slot is free)
 * rooms/<roomId>/live/{text,senderUid,updatedAt}
 * rooms/<roomId>/createdAt
 * users/<uid>/{displayName,roomId,fcmToken}
 * ```
 *
 * `roomSlots` exists so a stranger can be told "not found" versus "full" — the two errors
 * the UI must distinguish — without ever being able to read who is in the room. It holds a
 * single boolean and no identity, and the security rules only let it move `false -> true`
 * (and `true -> false` by the member recorded in `rooms/<id>/member/uid`).
 *
 * Concurrency
 * -----------
 * Claiming goes through a Realtime Database transaction on `roomSlots/<id>`, and the rules
 * independently forbid the second `false -> true`. So exclusivity is guaranteed twice over:
 * the transaction re-reads committed state, and the rules reject a losing write. A third
 * person is refused server-side, not by a client-side check.
 */
class RoomRepository(
    private val database: FirebaseDatabase = FirebaseProvider.database,
) {

    private fun roomRef(roomId: String) = database.reference.child(Paths.room(roomId))
    private fun slotRef(roomId: String) = database.reference.child(Paths.slot(roomId))
    private fun userRef(uid: String) = database.reference.child(Paths.user(uid))

    // ---------------------------------------------------------------- connection

    /**
     * Realtime Database link state for *this device*.
     *
     * It deliberately never asserts the partner received anything: while offline that
     * cannot be known, and claiming otherwise would be a lie shown as a status label.
     */
    val connection: Flow<ConnectionState> = database.reference
        .child(".info/connected")
        .snapshotFlow()
        .map { if (it.getValue(Boolean::class.java) == true) ConnectionState.Connected else ConnectionState.Offline }
        .catch { emit(ConnectionState.Offline) }

    // ---------------------------------------------------------------- create

    /**
     * Creates a room owned by [uid] and returns its ID.
     *
     * Creation is a transaction on the room node itself: the ID is taken atomically, and if
     * it somehow already exists the transaction aborts and a fresh random ID is generated.
     */
    suspend fun createRoom(uid: String, displayName: String): RoomResult<String> {
        var lastError: RoomError = RoomError.Unknown("Could not allocate a room ID")
        repeat(MAX_ID_ATTEMPTS) {
            val candidate = RoomId.generate()
            when (val outcome = createRoomWithId(uid, displayName, candidate)) {
                is RoomResult.Success -> return outcome
                is RoomResult.Failure -> {
                    lastError = outcome.error
                    Log.w(TAG, "createRoom attempt for $candidate failed: ${outcome.error}")
                }
            }
        }
        return RoomResult.Failure(lastError)
    }

    /**
     * Writes a new room.
     *
     * There is deliberately **no** `live` node in the payload. `live` is a uid-keyed map of
     * statuses, and a member who has published nothing has no entry in it — so the room is born
     * with no `live` node at all, and the first `setLiveText` creates one. Writing an empty
     * placeholder used to put `text`/`senderUid`/`updatedAt` directly under `live`, where they
     * read as a status belonging to a member called `senderUid` whose value happens to be a uid.
     * Nothing looked them up, so it was invisible, but it is exactly the shape the parser has to
     * defend against, and the honest thing is not to write it.
     */
    private suspend fun createRoomWithId(
        uid: String,
        displayName: String,
        roomId: String,
    ): RoomResult<String> = firebaseWithTimeout(what = "room creation") {
        val payload = mapOf(
            Paths.OWNER to mapOf(Paths.UID to uid, Paths.NAME to displayName),
            "createdAt" to ServerValue.TIMESTAMP,
        )

        // A single atomic `setValue`, not a transaction.
        //
        // A transaction would have to *read* the room node first, and the security rules only
        // let members read a room — the creator is not a member yet, so the transaction could
        // never run. Correctness therefore comes from the rules instead: `owner.uid` may only
        // be written as `auth.uid` on a node that does not yet exist, so of two clients that
        // somehow generate the same ID, the first to commit wins and the second is *rejected*
        // rather than silently overwriting it. The caller retries with a fresh ID.
        val created = runCatching { roomRef(roomId).setValue(payload).await() }.isSuccess
        if (!created) {
            return@firebaseWithTimeout RoomResult.Failure(
                RoomError.Unknown("Room ID $roomId is already taken"),
            )
        }

        // The public slot hint is best-effort: losing it only degrades the "room not found"
        // versus "room full" messages for a third person, so it must not fail creation.
        slotRef(roomId).setValue(false).awaitQuietly()
        userRef(uid).updateChildren(
            mapOf("displayName" to displayName, "roomId" to roomId),
        ).awaitQuietly()

        RoomResult.Success(roomId)
    }

    // ---------------------------------------------------------------- join

    /**
     * Atomically claims the second slot in [roomIdInput].
     *
     * Exactly one of two simultaneous joiners succeeds; the loser is told the room is full.
     */
    suspend fun joinRoom(uid: String, displayName: String, roomIdInput: String): RoomResult<String> {
        val roomId = RoomId.normalize(roomIdInput)
            ?: return RoomResult.Failure(RoomError.InvalidId(roomIdInput))

        return firebaseWithTimeout(what = "join") {
            when (val claim = slotRef(roomId).runClaimTransaction()) {
                ClaimOutcome.NotFound ->
                    RoomResult.Failure(RoomError.NotFound)

                ClaimOutcome.Full ->
                    RoomResult.Failure(RoomError.Full)

                ClaimOutcome.Denied -> {
                    // The rules refused. Work out whether the room vanished or was taken.
                    when {
                        !slotRef(roomId).existsOnce() -> RoomResult.Failure(RoomError.NotFound)
                        else -> RoomResult.Failure(RoomError.Full)
                    }
                }

                ClaimOutcome.Claimed -> {
                    // Attach identity. On failure, hand the slot back so the room is not
                    // left permanently half-occupied.
                    val attached = runCatching {
                        roomRef(roomId).child(Paths.MEMBER).updateChildren(
                            mapOf(Paths.UID to uid, Paths.NAME to displayName),
                        ).await()
                    }.isSuccess

                    if (!attached) {
                        slotRef(roomId).setValue(false).awaitQuietly()
                        RoomResult.Failure(RoomError.Unknown("Could not attach to room $roomId"))
                    } else {
                        userRef(uid).updateChildren(
                            mapOf("displayName" to displayName, "roomId" to roomId),
                        ).awaitQuietly()
                        RoomResult.Success(roomId)
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- leave

    /**
     * Removes the partner from this room, for the owner.
     *
     * ## Why this exists
     *
     * A partner who uninstalls the app leaves their uid recorded in the room forever. Nothing
     * in the schema can tell a departed member from one who is merely quiet, so the slot stays
     * taken: every joiner is told the room is full, and the owner is locked into a connection
     * with somebody who can never answer. For an app whose entire premise is two people in a
     * room, that is a dead end with no way out of it from inside the app.
     *
     * Only the owner can do this, which is the point. Ownership is the only claim on a room the
     * rules can still verify when the other person is gone — the departed member is precisely
     * the person who can no longer authorise anything, including their own removal.
     *
     * The room id is deliberately kept. The point is to be able to invite somebody else, not
     * to start again.
     */
    suspend fun removePartner(uid: String, roomId: String): RoomResult<Unit> =
        firebaseWithTimeout(what = "partner removal") {
            val snapshot = roomRef(roomId).snapshotFlow().firstSnapshotOrNull()
                ?: return@firebaseWithTimeout RoomResult.Failure(RoomError.NotFound)

            val ownerUid = snapshot.childStringOrNull(Paths.OWNER, Paths.UID)
            if (ownerUid != uid) {
                // Not ours to empty. A member asking to be removed is just leaving.
                return@firebaseWithTimeout RoomResult.Failure(RoomError.NotAMember)
            }

            val memberUid = snapshot.childStringOrNull(Paths.MEMBER, Paths.UID)
            if (memberUid == null) {
                // Already empty. Reported as success so a second tap is not an error.
                return@firebaseWithTimeout RoomResult.Success(Unit)
            }

            val removed = runCatching {
                roomRef(roomId).updateChildren(mapOf(Paths.MEMBER to null)).await()
            }.isSuccess

            if (!removed) {
                return@firebaseWithTimeout RoomResult.Failure(
                    RoomError.Unknown("Could not remove the partner from room $roomId"),
                )
            }

            // Freed after the member node is cleared, and permitted because the caller is the
            // owner: the rules allow an owner to open the hint, which is what lets somebody
            // else join. Clearing it before would work too, but this order leaves the room
            // briefly looking full rather than briefly looking joinable, and the first is the
            // safe direction to be wrong in.
            slotRef(roomId).setValue(false).awaitQuietly()

            // The departed member's own record still points at this room, which is what makes
            // their device try to rejoin on next launch. It is a courtesy: the rules do not
            // require it, and the write is best-effort so a failure cannot fail the removal.
            userRef(memberUid).child("roomId").setValue(null).awaitQuietly()

            RoomResult.Success(Unit)
        }

    /**
     * Leaves the current room.
     *
     * - A member leaving frees the slot, so the room can be joined again.
     * - An owner leaving while a partner is present transfers ownership to that partner and
     *   keeps the same room ID, so the connection is not destroyed.
     * - An owner leaving alone deletes the room and its slot hint.
     */
    suspend fun leaveRoom(uid: String, roomId: String): RoomResult<Unit> =
        firebaseWithTimeout(what = "leave") {
            val snapshot = roomRef(roomId).snapshotFlow().firstSnapshotOrNull()
                ?: return@firebaseWithTimeout RoomResult.Failure(RoomError.NotFound)

            if (!snapshot.belongsTo(uid)) {
                clearUserRoom(uid)
                return@firebaseWithTimeout RoomResult.Success(Unit)
            }

            val ownerUid = snapshot.childStringOrNull(Paths.OWNER, Paths.UID)
            val memberUid = snapshot.childStringOrNull(Paths.MEMBER, Paths.UID)
            val memberName = snapshot.childStringOrNull(Paths.MEMBER, Paths.NAME)
            val isOwner = ownerUid == uid

            val changed = runCatching {
                when {
                    isOwner && memberUid != null -> {
                        // Hand the room to the remaining member, keeping the room ID.
                        roomRef(roomId).updateChildren(
                            mapOf(
                                Paths.OWNER to mapOf(
                                    Paths.UID to memberUid,
                                    Paths.NAME to (memberName ?: ""),
                                ),
                                Paths.MEMBER to null,
                            ),
                        ).await()
                        slotRef(roomId).setValue(false).awaitQuietly()
                    }

                    isOwner -> {
                        roomRef(roomId).setValue(null).await()
                        slotRef(roomId).setValue(null).awaitQuietly()
                    }

                    else -> {
                        // The slot is freed *before* the member node is removed, and the order
                        // is load-bearing rather than incidental.
                        //
                        // The rules only let a member release the slot hint while they are
                        // still recorded in the room — that is what stops anybody walking up
                        // to an arbitrary room and flipping its public hint. Clear the member
                        // first and the release is no longer authorisable, because by then
                        // there is no member left to prove who is asking. The room then keeps a
                        // `true` hint forever, every joiner is told it is full, and a room
                        // that is genuinely empty can never be joined again.
                        slotRef(roomId).setValue(false).awaitQuietly()
                        roomRef(roomId).updateChildren(
                            mapOf(Paths.MEMBER to null),
                        ).await()
                    }
                }
            }.isSuccess

            clearUserRoom(uid)

            if (changed) RoomResult.Success(Unit)
            else RoomResult.Failure(RoomError.Unknown("Could not leave room $roomId"))
        }

    private suspend fun clearUserRoom(uid: String) {
        userRef(uid).child("roomId").setValue(null).awaitQuietly()
    }

    /**
     * Mirrors the display name into the user record.
     *
     * The copy written into the room is the one a partner can read; this record is the
     * canonical one for the owner of the UID.
     */
    suspend fun setDisplayName(uid: String, displayName: String) {
        userRef(uid).child("displayName").setValue(displayName.trim()).await()
    }

    // ---------------------------------------------------------------- live text

    /**
     * Publishes [uid]'s own status into the room.
     *
     * The write is scoped to `live/$uid`, so two members publishing at the same instant are two
     * independent writes rather than a race over one value. The database rules refuse the write
     * unless `auth.uid === $uid`, which is what makes "you can only set your own status" a
     * property of the data rather than of the UI.
     *
     * Clearing writes an empty string rather than removing the node, so a member who has never
     * published is distinguishable from one who has published nothing — both are "not saying
     * anything", but the second is a decision rather than an absence.
     */
    suspend fun setLiveText(uid: String, roomId: String, text: String) {
        roomRef(roomId).child(Paths.LIVE).child(uid).updateChildren(
            mapOf(
                "text" to text.take(Room.MAX_TEXT_LENGTH),
                "updatedAt" to ServerValue.TIMESTAMP,
            ),
        ).await()
    }

    /** Clears [uid]'s status. */
    suspend fun clearLiveText(uid: String, roomId: String) {
        setLiveText(uid, roomId, "")
    }

    // ---------------------------------------------------------------- observation

    /**
     * Observes a room for [uid].
     *
     * Emits [RoomObservation.Loading], then resolves to [RoomObservation.Ready] or
     * [RoomObservation.Unavailable]. A listener callback always arrives — including for a
     * node that does not exist — so there is no path on which the caller waits forever.
     */
    fun observeRoom(roomId: String, uid: String?): Flow<RoomObservation> = flow {
        emit(RoomObservation.Loading)
        emitAll(
            roomRef(roomId)
                .snapshotFlow()
                .map { snapshot ->
                    val room = snapshot.toRoomOrNull(roomId)
                    when {
                        room == null -> RoomObservation.Unavailable
                        uid != null && !room.isMember(uid) -> RoomObservation.Unavailable
                        else -> RoomObservation.Ready(room)
                    }
                }
                .catch { cause ->
                    // Losing read access means "no longer a member", which is a state rather
                    // than a crash. Anything unexpected is still logged, not swallowed.
                    Log.w(TAG, "observeRoom($roomId) ended: $cause")
                    emit(RoomObservation.Unavailable)
                },
        )
    }

    /** One-shot existence check for a room's public slot hint. */
    suspend fun roomExists(roomId: String): Boolean = firebaseWithTimeout(what = "room lookup") {
        slotRef(roomId).existsOnce()
    }

    /**
     * Reads a room once and returns it, or null if it is gone or [uid] is not a member of it.
     *
     * A single read rather than a subscription, for callers that are not a screen. The
     * notification's reply receiver is the motivating case: it has just written a status and needs
     * to know what this device's Live Update should now say, once, and then to be finished with.
     * Opening a listener there would keep the process alive for no reason and would need its own
     * teardown inside a ten-second broadcast window.
     *
     * Returns null rather than throwing for the same reason [observeRoom] resolves to
     * [RoomObservation.Unavailable]: not being able to see the room is a state, not a fault.
     */
    suspend fun readRoomOnce(roomId: String, uid: String?): Room? =
        firebaseWithTimeout(what = "room read") {
            val snapshot = roomRef(roomId).snapshotFlow().firstSnapshotOrNull()
            val room = snapshot?.toRoomOrNull(roomId)
            if (room == null || !room.isMember(uid)) null else room
        }

    private companion object {
        const val TAG = "RoomRepository"
        const val MAX_ID_ATTEMPTS = 6
    }
}

// ------------------------------------------------------------------ internals

private enum class ClaimOutcome { Claimed, NotFound, Full, Denied }

/**
 * Atomically flips the public slot hint from open to taken.
 *
 * The transaction re-reads committed state before committing, so simultaneous joiners are
 * serialised. The security rules independently reject a second `false -> true`, so even a
 * client that skipped the transaction could not take a taken slot.
 */
private suspend fun DatabaseReference.runClaimTransaction(): ClaimOutcome {
    val outcome = AtomicReference(ClaimOutcome.Denied)
    awaitTransaction(object : Transaction.Handler {
        override fun doTransaction(current: MutableData): Transaction.Result {
            if (current.isAbsent()) {
                outcome.set(ClaimOutcome.NotFound)
                return Transaction.abort()
            }
            if (current.getValue(Boolean::class.java) == true) {
                outcome.set(ClaimOutcome.Full)
                return Transaction.abort()
            }
            current.value = true
            return Transaction.success(current)
        }

        override fun onComplete(error: DatabaseError?, committed: Boolean, current: DataSnapshot?) {
            outcome.set(
                when {
                    committed -> ClaimOutcome.Claimed
                    error != null -> ClaimOutcome.Denied
                    else -> outcome.get()
                },
            )
        }
    })
    return outcome.get()
}

private suspend fun DatabaseReference.existsOnce(): Boolean =
    snapshotFlow().firstExists()

private fun DataSnapshot.belongsTo(uid: String): Boolean =
    childStringOrNull(Paths.OWNER, Paths.UID) == uid ||
        childStringOrNull(Paths.MEMBER, Paths.UID) == uid

private fun DataSnapshot.toRoomOrNull(roomId: String): Room? {
    if (!exists()) return null
    val ownerUid = childStringOrNull(Paths.OWNER, Paths.UID) ?: return null
    return Room(
        id = roomId,
        ownerUid = ownerUid,
        memberUid = childStringOrNull(Paths.MEMBER, Paths.UID),
        ownerName = childStringOrNull(Paths.OWNER, Paths.NAME).orEmpty(),
        memberName = childStringOrNull(Paths.MEMBER, Paths.NAME),
        live = child(Paths.LIVE).toLiveTexts(),
        createdAt = child("createdAt").getValue(Long::class.java) ?: 0L,
    )
}

/**
 * Reads `live` as a uid-keyed map of statuses.
 *
 * A member who has never published has no node here at all, which is simply an absent key; the
 * map lookup in [LiveTexts.textOf] turns that into an empty string. A malformed value (one that
 * is not an object, or whose `text` is not a string) is skipped rather than allowed to take the
 * whole room down — a bad node should cost one status, not the room.
 */
private fun DataSnapshot.toLiveTexts(): LiveTexts {
    if (!exists()) return LiveTexts.Empty
    val byUid = buildMap {
        for (entry in children) {
            // Only an object with a string `text` is a status. A bare primitive under `live` is
            // either a hand-edited node or a leftover from the old single-slot schema, and neither
            // is anybody's status.
            if (!entry.hasChildren()) continue
            val uid = entry.key ?: continue
            val text = entry.child("text").getValue(String::class.java) ?: continue
            put(uid, text)
        }
    }
    return LiveTexts(byUid = byUid)
}
