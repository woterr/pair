/**
 * Pair — remote Live Update delivery.
 *
 * Why this exists
 * ---------------
 * The Realtime Database is the source of truth for a room's live statuses, and every client
 * listens to it. But a database listener only exists while the app's process is alive. If the
 * receiving device has Pair closed or killed, nothing is listening, and the update is not
 * observed at all. FCM is the wake-up path: this function pushes the new value to the *other*
 * member's device, which then posts or updates its Live Update.
 *
 * This is the whole product. Without it, a status only reaches your partner when they happen to
 * have the app open, which is the opposite of a live status.
 *
 * The schema this watches
 * -----------------------
 * `rooms/{roomId}/live/{uid}` — one node per member, each holding that member's own status:
 *
 *     live: {
 *       "<uid A>": { text: "At the gym", updatedAt: 1759... },
 *       "<uid B>": { text: "On the bus", updatedAt: 1759... },
 *     }
 *
 * It used to be a single `live: { text, senderUid, updatedAt }` that both members wrote over.
 * That shape cannot carry two independent statuses, and the trigger here was written against it:
 * it watched `/rooms/{roomId}/live` with `onValueCreated`, which only fires when the *whole*
 * node is created. The first status a room ever had created it and pushed; every status after
 * that updated a child and pushed nothing. The receiving device therefore only ever saw updates
 * made while the app was in the foreground and its database listener was alive — which is exactly
 * the bug where a Live Update only changes once you open the app.
 *
 * So: the trigger is now on the per-uid child, and it is `onValueWritten` rather than
 * `onValueCreated`, so a status that *changes* pushes as well as one that first appears.
 *
 * Contract
 * --------
 * The keys below must stay in step with `com.wood.pair.fcm.PairFcmContract` in the app.
 *
 * This is the only place that holds a credential capable of sending a push. The Android app
 * contains only public client configuration and cannot send to itself.
 */

import { initializeApp } from "firebase-admin/app";
import { getDatabase } from "firebase-admin/database";
import { getMessaging } from "firebase-admin/messaging";
import { onValueWritten } from "firebase-functions/v2/database";

initializeApp();

const REGION = "asia-southeast1";

/** Mirrors RoomId.ALPHABET in the app: 32 unambiguous uppercase alphanumerics. */
const ROOM_ID_PATTERN = /^[A-HJ-NP-Z2-9]{6}$/;

/** Mirrors Room.MAX_TEXT_LENGTH in the app. */
const MAX_TEXT_LENGTH = 500;

/** One member's status, as stored under `live/{uid}`. */
interface LiveEntry {
  text?: string;
  updatedAt?: number;
}

interface RoomRecord {
  owner?: { uid?: string; name?: string };
  member?: { uid?: string; name?: string };
}

/**
 * Sent when a member's status node is deleted, so the partner's chip comes down instead of
 * freezing on text that no longer exists.
 *
 * Empty rather than omitted: the app's own paused state is "the text is blank", and reusing that
 * means the withdrawal travels the same path as every other change with no new handling on the
 * receiving side.
 */
const CLEARED = "";

export const onLiveTextChanged = onValueWritten(
  { ref: "/rooms/{roomId}/live/{uid}", region: REGION },
  async (event) => {
    const roomId = event.params.roomId;
    // The uid is the *key*, not a field. That is the whole point of the per-member schema, and
    // reading it from the path rather than the payload is what makes it unforgeable: a member
    // cannot write a status under someone else's key, because the database rules reject it.
    const senderUid = event.params.uid;

    if (!roomId || !senderUid) {
      return;
    }

    // Defensive: this endpoint is reachable by anyone who can write to the database, so the
    // shape of the path is checked before it is trusted.
    if (!ROOM_ID_PATTERN.test(roomId)) {
      console.warn(`Ignoring live update for malformed room id: ${roomId}`);
      return;
    }

    const after = event.data.after.val() as LiveEntry | null;
    const text = after?.text;

    if (text !== undefined && (typeof text !== "string" || text.length > MAX_TEXT_LENGTH)) {
      console.warn(`Ignoring malformed live payload for room ${roomId}`);
      return;
    }

    // A deleted node, or one with no text, is a withdrawal. We still push, because the partner's
    // chip is showing the old words and only this message can take it down.
    const outboundText = typeof text === "string" ? text : CLEARED;
    const updatedAt =
      typeof after?.updatedAt === "number" ? after.updatedAt : Date.now();

    // Re-read the room rather than trusting the write alone. The Admin SDK bypasses security
    // rules, so this function is responsible for confirming the sender really is in this room
    // and deciding who the recipient is.
    const roomSnapshot = await getDatabase().ref(`rooms/${roomId}`).get();
    const room = roomSnapshot.val() as RoomRecord | null;

    if (!room) {
      console.warn(`Room ${roomId} vanished before the live update could be delivered`);
      return;
    }

    const ownerUid = room.owner?.uid;
    const memberUid = room.member?.uid;

    if (senderUid !== ownerUid && senderUid !== memberUid) {
      console.warn(
        `Sender ${senderUid} is not a member of room ${roomId}; refusing to forward`,
      );
      return;
    }

    // The recipient is whoever the sender is not. With no second member there is nobody to
    // tell: the sender's own device already shows its own Live Update, so pushing to it
    // would be a wasted message and a visible duplicate.
    const recipientUid = senderUid === ownerUid ? memberUid : ownerUid;
    if (!recipientUid) {
      return;
    }

    const senderName =
      senderUid === ownerUid ? room.owner?.name : room.member?.name;

    const tokenSnapshot = await getDatabase()
      .ref(`users/${recipientUid}/fcmToken`)
      .get();
    const token = tokenSnapshot.val();

    if (typeof token !== "string" || token.length === 0) {
      // Not an error worth failing on: the recipient has not registered yet, and the Realtime
      // Database listener will still pick the value up if the app is open.
      console.log(`No FCM token registered for ${recipientUid}`);
      return;
    }

    try {
      await getMessaging().send({
        token,
        // Data-only. A notification payload would let the system post its own notification
        // and bypass the app's Live Update entirely, including on Android 16+.
        data: {
          roomId,
          text: outboundText,
          senderUid,
          senderName: typeof senderName === "string" ? senderName : "",
          updatedAt: String(updatedAt),
        },
        android: {
          // The app builds and owns the notification, so nothing is rendered here. High
          // priority is what makes delivery prompt enough for a status bar chip, and it is also
          // what grants the temporary allowlist Pair needs to start its foreground service from
          // the background.
          priority: "high",
        },
      });
    } catch (error) {
      // A stale or unregistered token throws. Log it and let the normal Realtime Database
      // path cover the case; a failed send must not retry-loop.
      console.error(`Failed to send live update to ${recipientUid}`, error);
    }
  },
);
