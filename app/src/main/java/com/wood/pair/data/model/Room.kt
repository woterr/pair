package com.wood.pair.data.model

/** Which of the two slots a user occupies. */
enum class MemberSlot { Owner, Member }

/**
 * A room as this device sees it.
 *
 * Names are denormalised into the room on purpose. A member must be able to read their
 * partner's display name, but the security rules deliberately do *not* let a user read
 * another user's profile record, so the name has to live inside the room where
 * membership already grants access.
 */
data class Room(
    val id: String,
    val ownerUid: String,
    val memberUid: String?,
    val ownerName: String,
    val memberName: String?,
    val live: LiveTexts,
    val createdAt: Long,
) {
    fun isMember(uid: String?): Boolean =
        uid != null && (uid == ownerUid || uid == memberUid)

    fun hasPartner(): Boolean = memberUid != null

    /** Full room: the owner plus a claimed second slot. */
    fun isFull(): Boolean = memberUid != null

    fun slotOf(uid: String?): MemberSlot? = when (uid) {
        ownerUid -> MemberSlot.Owner
        memberUid -> MemberSlot.Member
        else -> null
    }

    fun nameOf(uid: String?): String? = when (uid) {
        ownerUid -> ownerName
        memberUid -> memberName
        else -> null
    }

    /** The other member's uid from [uid]'s point of view, or null when they are alone. */
    fun partnerOf(uid: String?): String? = when (uid) {
        ownerUid -> memberUid
        memberUid -> ownerUid
        else -> null
    }

    /** The other member's display name from [uid]'s point of view, or null when they are alone. */
    fun partnerNameOf(uid: String?): String? = when (uid) {
        ownerUid -> memberName
        memberUid -> ownerName
        else -> null
    }

    companion object {
        /** Upper bound for one person's live text, enforced in the database rules as well. */
        const val MAX_TEXT_LENGTH: Int = 500
    }
}

/** A user as stored by the app in the Realtime Database. */
data class UserProfile(
    val uid: String,
    val displayName: String,
    val roomId: String?,
    val fcmToken: String?,
)

/**
 * Firebase Realtime Database connection state, surfaced in the UI.
 *
 * Deliberately does not claim the partner received anything: [Connected] only means this
 * device's link to the database is up.
 */
enum class ConnectionState {
    /** Link is down. Local state may still be pending. */
    Offline,

    /** Link is up and the server has acknowledged our last write. */
    Connected,
}
