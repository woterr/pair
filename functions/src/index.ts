/**
 * Pair — remote Live Update delivery.
 *
 * Why this exists
 * ---------------
 * The Realtime Database is the source of truth for a room's single live text, and every
 * client listens to it. But a database listener only exists while the app's process is
 * alive. If the receiving device has Pair closed or killed, nothing is listening, and the
 * update is not observed at all. FCM is the wake-up path: this function pushes the new
 * value to the *other* member's device, which then posts or updates its Live Update.
 *
 * Contract
 * --------
 * These keys must stay in step with `com.wood.pair.fcm.PairFcmContract` in the app.
 *
 * This is the only place that holds a credential capable of sending a push. The Android app
 * contains only public client configuration and cannot send to itself.
 */

import { initializeApp } from "firebase-admin/app";
import { getDatabase } from "firebase-admin/database";
import { getMessaging } from "firebase-admin/messaging";
import { onValueCreated } from "firebase-functions/v2/database";

initializeApp();

const REGION = "asia-southeast1";

/** Mirrors RoomId.ALPHABET in the app: 32 unambiguous uppercase alphanumerics. */
const ROOM_ID_PATTERN = /^[A-HJ-NP-Z2-9]{6}$/;

/** Mirrors Room.MAX_TEXT_LENGTH in the app. */
const MAX_TEXT_LENGTH = 500;

interface LivePayload {
  text: string;
  senderUid: string;
  updatedAt: number;
}

interface RoomRecord {
  owner?: { uid?: string; name?: string };
  member?: { uid?: string; name?: string };
  live?: LivePayload;
}

export const onLiveTextChanged = onValueCreated(
  { ref: "/rooms/{roomId}/live", region: REGION },
  async (event) => {
    const roomId = event.params.roomId;
    const snapshot = event.data;

    if (!snapshot.exists()) {
      return;
    }

    // Defensive: this is a public-ish endpoint, so nothing is trusted.
    if (!ROOM_ID_PATTERN.test(roomId)) {
      console.warn(`Ignoring live update for malformed room id: ${roomId}`);
      return;
    }

    const live = snapshot.val() as LivePayload | null;
    if (
      !live ||
      typeof live.text !== "string" ||
      typeof live.senderUid !== "string" ||
      live.text.length > MAX_TEXT_LENGTH
    ) {
      console.warn(`Ignoring malformed live payload for room ${roomId}`);
      return;
    }

    // Re-read the room rather than trusting the payload alone. The Admin SDK bypasses
    // security rules, so this function is responsible for confirming the sender really is in
    // this room and deciding who the recipient is.
    const roomSnapshot = await getDatabase()
      .ref(`rooms/${roomId}`)
      .get();
    const room = roomSnapshot.val() as RoomRecord | null;

    if (!room) {
      console.warn(`Room ${roomId} vanished before the live update could be delivered`);
      return;
    }

    const ownerUid = room.owner?.uid;
    const memberUid = room.member?.uid;
    const senderUid = live.senderUid;

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
      // Not an error worth failing on: the recipient has not registered yet, and the
      // Realtime Database listener will still pick the value up if the app is open.
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
          text: live.text,
          senderUid,
          senderName: typeof senderName === "string" ? senderName : "",
          updatedAt: String(live.updatedAt ?? Date.now()),
        },
        android: {
          // The app builds and owns the notification, so nothing is rendered here.
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
