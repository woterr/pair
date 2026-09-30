package com.wood.pair.fcm

import com.wood.pair.data.model.Room
import com.wood.pair.data.model.RoomId

/**
 * The contract for Pair's FCM messages.
 *
 * Kept in one object so the Android client and the Cloud Function cannot drift apart. The
 * Cloud Function's copy of these keys lives in `functions/src/index.ts` — if one changes,
 * both must change.
 *
 * Messages are **data-only**. A data-only message is required here: it is delivered in the
 * background regardless of app state, and it does not let the system post its own
 * notification, which would bypass the Live Update entirely.
 */
object PairFcmContract {

    /** Room the message refers to. */
    const val KEY_ROOM_ID = "roomId"

    /** The room's full live text at the time of the change. */
    const val KEY_LIVE_TEXT = "text"

    /** UID of the person whose keystroke produced this. */
    const val KEY_SENDER_UID = "senderUid"

    /** Server-side timestamp of the change, in milliseconds. */
    const val KEY_UPDATED_AT = "updatedAt"

    /** Display name of the sender, so the notification can attribute the text. */
    const val KEY_SENDER_NAME = "senderName"

    /**
     * Validates a raw payload.
     *
     * Anything that is not a real Pair room with real text is rejected rather than posted, so
     * a malformed or spoofed message cannot drive a Live Update.
     */
    fun validate(
        roomId: String?,
        liveText: String?,
        senderUid: String?,
    ): Boolean {
        if (roomId == null || !RoomId.isValid(roomId)) return false
        if (senderUid.isNullOrBlank()) return false
        if (liveText == null) return false
        if (liveText.length > Room.MAX_TEXT_LENGTH) return false
        return true
    }
}
