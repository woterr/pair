/**
 * The rules, exercised through the writes the app actually makes.
 *
 * Why this suite exists separately from `database.rules.test.mjs`
 * ---------------------------------------------------------------
 * That suite asks "is the rule right?". This one asks "does the rule let the app work?",
 * and it is derived from `RoomRepository` rather than from the rules file. It writes where
 * the app writes, at the depth the app writes, with the values the app sends.
 *
 * The distinction is not academic, and getting it wrong is invisible until it is not.
 *
 * `createRoomWithId` creates a room with a single
 * `rooms/{roomId}.setValue({ owner, createdAt })` — a write **at the room node**. The other
 * suite tested `rooms/{roomId}/owner/uid` — a write at a **child** of the room node. Firebase
 * only honours a `.write` rule at the write location or an ancestor of it, never below it, so
 * a grant that works at `owner/uid` cannot authorise a write at `rooms/{roomId}`. Every
 * creation-path assertion passed, room creation was never exercised at all, and it broke the
 * moment the rules were deployed.
 *
 * The second thing it caught is a gap no amount of asking "is the rule right?" would find:
 * a room whose partner has uninstalled is permanently unjoinable, because nothing in the
 * schema could clear a member slot. The owner can now evict them.
 *
 * Each scenario gets its own room. Tests that share one room share one mutable truth, and a
 * single unexpected failure silently changes the meaning of every assertion after it — which
 * is how this file was wrong twice before it was right.
 */

import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";
import {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} from "@firebase/rules-unit-testing";

const here = dirname(fileURLToPath(import.meta.url));
const RULES = readFileSync(resolve(here, "..", "database.rules.json"), "utf8");

const A = "uidALICE";
const B = "uidBOB";
const C = "uidCAROL";
const D = "uidDANA";

/** A room nobody is in, with no slot hint yet: exactly what `createRoom` is handed. */
const CREATED = "RSTUVW";
/** Solo, with its slot hint open. Someone joins, then leaves. */
const JOIN_LEAVE = "ABCDEF";
/** Solo, for the owner to walk away from and delete. */
const SOLO_LEAVE = "BCDEFG";
/** Two members, one of whom has since uninstalled. The eviction scenario. */
const EVICT = "KLMNPQ";
/** Two live members, for the properties that must hold on a healthy room. */
const FULL = "YZ2345";

let env;
const asUser = (uid) => env.authenticatedContext(uid).database();

const results = [];
async function it(label, fn) {
  try {
    await fn();
    results.push([true, label]);
    console.log(`PASS  ${label}`);
  } catch (e) {
    results.push([false, label]);
    console.log(`FAIL  ${label}\n        ${String(e.message).split("\n")[0]}`);
  }
}

/** Sets a room up directly, as if a previous session had left it in that state. */
async function seed(ctx, roomId, owner, member, { withSlot = true } = {}) {
  await ctx.database().ref(`rooms/${roomId}`).set({
    owner: { uid: owner, name: "Owner" },
    ...(member ? { member: { uid: member, name: "Partner" } } : {}),
    createdAt: 1759000000000,
  });
  if (withSlot) {
    await ctx.database().ref(`roomSlots/${roomId}`).set(member ? true : false);
  }
}

(async () => {
  try {
    JSON.parse(RULES);
    console.log("database.rules.json parses as strict JSON: yes\n");
  } catch (e) {
    console.log(`database.rules.json parses as strict JSON: NO — ${e.message}\n`);
  }

  env = await initializeTestEnvironment({
    projectId: "pair-4791e",
    database: { rules: RULES, host: "127.0.0.1", port: 9000 },
  });

  await env.withSecurityRulesDisabled(async (ctx) => {
    await ctx.database().ref("/").remove();
    // CREATED is deliberately *not* seeded: creating a room is a write to a node that does
    // not exist yet, and seeding one would test the "already taken" path by accident.
    await seed(ctx, JOIN_LEAVE, A, null);
    await seed(ctx, SOLO_LEAVE, A, null);
    await seed(ctx, EVICT, A, B);
    await seed(ctx, FULL, A, C);
  });

  // ------------------------------------------------------------------ create
  console.log("--- RoomRepository.createRoomWithId ---");
  await it("creating a room is a setValue at the room node", async () => {
    await assertSucceeds(
      asUser(C).ref(`rooms/${CREATED}`).set({
        owner: { uid: C, name: "Carol" },
        createdAt: 1759000000001,
      }),
    );
  });

  await it("the public slot hint can be claimed for a room just created", async () => {
    await assertSucceeds(asUser(C).ref(`roomSlots/${CREATED}`).set(false));
  });

  await it("the creator's own user record accepts the room id", async () => {
    await assertSucceeds(
      asUser(C).ref(`users/${C}`).update({ displayName: "Carol", roomId: CREATED }),
    );
  });

  await it("a room cannot be created on top of an existing room", async () => {
    // The whole node is the write, so this is also the test that two clients generating the
    // same id cannot both win: the second is refused rather than overwriting the first.
    await assertFails(
      asUser(D).ref(`rooms/${CREATED}`).set({
        owner: { uid: D, name: "Dana" },
        createdAt: 2,
      }),
    );
  });

  // ------------------------------------------------------------------ join
  console.log("\n--- RoomRepository.joinRoom ---");
  await it("an open room's slot can be claimed (false -> true)", async () => {
    await assertSucceeds(asUser(B).ref(`roomSlots/${JOIN_LEAVE}`).set(true));
  });

  await it("joining attaches identity with updateChildren at the member node", async () => {
    await assertSucceeds(
      asUser(B).ref(`rooms/${JOIN_LEAVE}/member`).update({
        uid: B,
        name: "Snoopy",
      }),
    );
  });

  await it("a member may name themselves when joining", async () => {
    // `member/name` has to accept a member naming themselves on the way in, not only the
    // owner naming them afterwards — the join writes both in a single call.
    await assertSucceeds(asUser(B).ref(`rooms/${JOIN_LEAVE}/member/name`).set("Snoopy II"));
  });

  // ------------------------------------------------------------------ leave
  console.log("\n--- RoomRepository.leaveRoom ---");
  await it("a member leaving frees the slot BEFORE removing the member node", async () => {
    // The order is load-bearing. The rules only let a member release the hint while they are
    // still recorded in the room, so removing the member first would leave a `true` hint
    // nobody can ever clear — and a genuinely empty room that can never be joined again.
    await assertSucceeds(asUser(B).ref(`roomSlots/${JOIN_LEAVE}`).set(false));
    await assertSucceeds(asUser(B).ref(`rooms/${JOIN_LEAVE}`).update({ member: null }));
  });

  await it("a member cannot free the slot once they are no longer in the room", async () => {
    // The other half of the same fact, asserted so the ordering cannot be reversed later.
    await assertFails(asUser(B).ref(`roomSlots/${JOIN_LEAVE}`).set(false));
  });

  await it("a room freed by a leaving member can be joined by somebody else", async () => {
    await assertSucceeds(
      asUser(D).ref(`rooms/${JOIN_LEAVE}/member`).update({
        uid: D,
        name: "Dana",
      }),
    );
  });

  await it("the owner leaving alone deletes the whole room", async () => {
    await assertSucceeds(asUser(A).ref(`rooms/${SOLO_LEAVE}`).set(null));
  });

  await it("a member cannot hand the room over to themselves", async () => {
    // Checked before the owner hands over, because afterwards the member *is* the owner and
    // the same write becomes legitimate. Ownership is the only claim the rules can verify
    // once the other person is gone, and a member promoting themselves is a takeover.
    await assertFails(
      asUser(C).ref(`rooms/${FULL}`).update({
        owner: { uid: C, name: "Carol" },
        member: null,
      }),
    );
  });

  await it("the owner leaving with a partner hands the room over", async () => {
    // The *owner* hands over, not the member. The room id survives, so the connection is not
    // destroyed and the remaining partner keeps the room.
    await assertSucceeds(
      asUser(A).ref(`rooms/${FULL}`).update({
        owner: { uid: C, name: "Carol" },
        member: null,
      }),
    );
  });

  // ------------------------------------------------------------------ eviction
  console.log("\n--- RoomRepository.removePartner ---");
  await it("the OWNER can evict an absent member", async () => {
    await assertSucceeds(asUser(A).ref(`rooms/${EVICT}`).update({ member: null }));
  });

  await it("eviction frees the public slot, so the room can be joined again", async () => {
    await assertSucceeds(asUser(A).ref(`roomSlots/${EVICT}`).set(false));
  });

  await it("after eviction the room can be joined by somebody new", async () => {
    await assertSucceeds(
      asUser(C).ref(`rooms/${EVICT}/member`).update({ uid: C, name: "Carol" }),
    );
  });

  await it("an evicted member cannot clear the slot afterwards", async () => {
    await assertFails(asUser(B).ref(`rooms/${EVICT}`).update({ member: null }));
  });

  await it("a member cannot reassign the room to themselves", async () => {
    // The member clearing their own node is a legitimate leave; taking the room over is not.
    await assertFails(
      asUser(C).ref(`rooms/${EVICT}`).update({
        owner: { uid: C, name: "Carol" },
        member: null,
      }),
    );
  });

  // ------------------------------------------------------------------ a healthy room
  console.log("\n--- properties that must hold on a room with two live members ---");
  await it("a third person cannot join a room that already has two members", async () => {
    await assertFails(
      asUser(D).ref(`rooms/${EVICT}/member`).update({ uid: D, name: "Dana" }),
    );
  });

  await it("a non-member cannot read the room", async () => {
    await assertFails(asUser(D).ref(`rooms/${EVICT}`).once("value"));
  });

  // The per-uid grant the whole live-status feature rests on has to survive the room-level
  // write added for creation and eviction. A blanket owner write that reached `live` would let
  // the owner publish a status in their partner's name, which is the one thing the schema is
  // arranged to make impossible.
  console.log("\n--- a status cannot be forged ---");
  await it("the owner cannot write a status under the member's key", async () => {
    await assertFails(
      asUser(A).ref(`rooms/${EVICT}/live/${C}`).set({ text: "forged", updatedAt: 1 }),
    );
  });

  await it("the owner cannot write the whole live map at once", async () => {
    await assertFails(
      asUser(A).ref(`rooms/${EVICT}/live`).set({
        [C]: { text: "forged", updatedAt: 2 },
      }),
    );
  });

  await it("each member can still write their own status", async () => {
    await assertSucceeds(
      asUser(A).ref(`rooms/${EVICT}/live/${A}`).set({ text: "mine", updatedAt: 3 }),
    );
    await assertSucceeds(
      asUser(C).ref(`rooms/${EVICT}/live/${C}`).set({ text: "theirs", updatedAt: 4 }),
    );
  });

  await it("a member cannot write a status under the owner's key", async () => {
    await assertFails(
      asUser(C).ref(`rooms/${EVICT}/live/${A}`).set({ text: "forged", updatedAt: 5 }),
    );
  });

  await env.cleanup();

  const failed = results.filter((r) => !r[0]);
  console.log(
    `\n=== ${results.length - failed.length}/${results.length} passed` +
      (failed.length ? `, ${failed.length} FAILED` : "") + " ===\n",
  );
  process.exit(failed.length ? 1 : 0);
})().catch((e) => {
  console.error(`\n${e.message}\n`);
  process.exit(1);
});
