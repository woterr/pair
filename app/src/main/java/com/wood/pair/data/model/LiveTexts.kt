package com.wood.pair.data.model

/**
 * The room's live texts — the status *each* member is broadcasting.
 *
 * ## Why this is keyed by uid and not a single room-wide value
 *
 * A room holds **two** independent statuses, one per member, and both are readable by both
 * members. That is the whole product: I am telling you where I am, you are telling me where you
 * are, and neither overwrites the other.
 *
 * A single `live.text` on the room cannot express that. With one slot, whoever writes last wins,
 * so A's status silently becomes B's — which is exactly the bug this replaces: the partner's
 * message arrived in *my* field, because there was only one field and it was mine by accident.
 *
 * Keying by uid makes the two independent by construction, and makes "whose status is this?"
 * answerable without a second read: a status is mine when its key is my uid.
 *
 * @param byUid the text each member is broadcasting, keyed by their uid. A uid absent from the
 *   map is a member who has not set anything, which is the same visible state as one who has
 *   cleared it.
 */
data class LiveTexts(
    val byUid: Map<String, String> = emptyMap(),
) {
    /** The text [uid] is broadcasting, or empty when they have set nothing. */
    fun textOf(uid: String?): String = if (uid == null) "" else byUid[uid].orEmpty()

    /** True when [uid] currently has something to say. */
    fun hasTextFor(uid: String?): Boolean = !textOf(uid).isBlank()

    /**
     * The text to put in [viewerUid]'s Live Update.
     *
     * With a partner present this is the **partner's** status, because that is the thing the user
     * is asking about: where is the person I am paired with. There is nothing useful in showing a
     * person their own words back at them while their partner is in the room.
     *
     * With no partner it is the viewer's own, because then the only status in existence is theirs
     * and it is the only thing the notification could say. That is the documented rule rather than
     * a fallback: "what I write is shown to me until a partner joins, and to them after" is how the
     * room behaves when someone is waiting.
     */
    fun forViewer(viewerUid: String?, partnerUid: String?): String =
        if (partnerUid != null) textOf(partnerUid) else textOf(viewerUid)

    companion object {
        val Empty = LiveTexts()
    }
}
