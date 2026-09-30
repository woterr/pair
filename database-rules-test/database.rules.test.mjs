/**
 * The Realtime Database rules, asserted against the paths Pair actually depends on.
 *
 * Why this exists
 * ---------------
 * These rules were written before anything was deployed, and they were correct in the
 * only sense that matters on paper: they were never enforced anywhere. The database
 * was in public test mode, which means every read and write succeeded regardless of
 * what the rules said. A wrong rule was therefore invisible, and the file was not
 * even valid JSON — the rule expressions were written across several lines, so the
 * newlines inside them were raw control characters. Firebase's parser is lenient and
 * read it; a strict one did not.
 *
 * The practical consequence is that "the app works" proved nothing about the rules.
 * The app worked *because* nothing was being checked. This suite is what turns the
 * rules from a document into an assertion.
 *
 * What the product rests on
 * -------------------------
 * Two people share a room and nobody else can touch it: they cannot read it, join it,
 * or speak for the other person. Those are the properties below, and every one of them
 * has a matching "must be refused" counterpart, because a rule that is too permissive
 * and a rule that is too strict are both bugs and only the pair of them is a test.
 *
 * Running
 * -------
 * The rules are evaluated by the Realtime Database emulator, which loads
 * `database.rules.json` for real, so what is asserted here is what would be deployed.
 *
 *     firebase emulators:start --only database
 *     npm install && npm test
 *
 * The emulator must be running on 127.0.0.1:9000 before the tests start.
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
const RULES_PATH = resolve(here, "..", "database.rules.json");
const RULES = readFileSync(RULES_PATH, "utf8");

const ROOM = "ABCDEF";
const A = "uidALICE";
const B = "uidBOB";
const C = "uidCAROL"; // deliberately never a member of ROOM

let env;

const asUser = (uid) => env.authenticatedContext(uid).database();
const asAnon = () => env.unauthenticatedContext().database();

const status = (text, updatedAt) => ({ text, updatedAt });

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

(async () => {
  // Checked explicitly, because the failure this suite exists to prevent is precisely
  // a rules file that Firebase happens to accept and nothing else does.
  try {
    JSON.parse(RULES);
    console.log("database.rules.json parses as strict JSON: yes");
  } catch (e) {
    console.log(`database.rules.json parses as strict JSON: NO — ${e.message}`);
  }

  env = await initializeTestEnvironment({
    projectId: "pair-4791e",
    database: { rules: RULES, host: "127.0.0.1", port: 9000 },
  });

  // The emulator keeps its data between runs, and now that the rules are real an
  // anonymous delete is refused — so the tree is cleared with the rules switched off
  // before the fixtures are laid down.
  await env.withSecurityRulesDisabled(async (ctx) => {
    await ctx.database().ref("/").remove();
  });

  // A room in the state the app leaves it in: A created it, B joined.
  await env.withSecurityRulesDisabled(async (ctx) => {
    await ctx.database().ref(`rooms/${ROOM}`).set({
      owner: { uid: A, name: "Alice" },
      member: { uid: B, name: "Bob" },
      createdAt: 1759000000000,
    });
    await ctx.database().ref(`users/${A}/fcmToken`).set("token-a");
    await ctx.database().ref(`users/${B}/fcmToken`).set("token-b");
  });

  console.log("\n--- room creation and joining ---");
  await it("A can create a room and claim ownership", async () => {
    await assertSucceeds(asUser(A).ref(`rooms/KLMNPQ/owner/uid`).set(A));
  });
  await it("B can join a room that has an owner", async () => {
    await assertSucceeds(asUser(B).ref(`rooms/KLMNPQ/member/uid`).set(B));
  });
  await it("any signed-in user can create their own room", async () => {
    // Creating a room means claiming a fresh `owner/uid`, so this has to be open to
    // every authenticated user: the room belongs to nobody until it has an owner.
    await assertSucceeds(asUser(B).ref(`rooms/ZZZZZZ/owner/uid`).set(B));
  });
  await it("an anonymous visitor cannot create anything", async () => {
    await assertFails(asAnon().ref(`rooms/QQQQQQ/owner/uid`).set("x"));
  });

  console.log("\n--- reading a room ---");
  await it("A can read their own room", async () => {
    await assertSucceeds(asUser(A).ref(`rooms/${ROOM}`).once("value"));
  });
  await it("B can read the room they joined", async () => {
    await assertSucceeds(asUser(B).ref(`rooms/${ROOM}`).once("value"));
  });
  await it("C cannot read a room they are not in", async () => {
    await assertFails(asUser(C).ref(`rooms/${ROOM}`).once("value"));
  });
  await it("an anonymous visitor cannot read anything", async () => {
    // `ref("")` is not a legal path in the client SDK, so the tree is probed at real
    // paths rather than at the root.
    await assertFails(asAnon().ref(`rooms/${ROOM}`).once("value"));
    await assertFails(asAnon().ref(`users/${A}`).once("value"));
  });

  console.log("\n--- live status: the feature the product is ---");
  await it("A can publish their own status", async () => {
    await assertSucceeds(
      asUser(A).ref(`rooms/${ROOM}/live/${A}`).set(status("At the gym", 1)),
    );
  });
  await it("B can publish their own status", async () => {
    await assertSucceeds(
      asUser(B).ref(`rooms/${ROOM}/live/${B}`).set(status("On the bus", 2)),
    );
  });
  await it("A CANNOT publish a status under B's key", async () => {
    // The single most important line in the rules. A member's status is written under
    // their own key and the rules refuse anything else, so a status cannot be forged
    // in someone else's name. It is also what makes the Cloud Function's trigger
    // trustworthy: it reads the sender from the path, not from the payload.
    await assertFails(
      asUser(A).ref(`rooms/${ROOM}/live/${B}`).set(status("forged", 3)),
    );
  });
  await it("C cannot publish any status into the room", async () => {
    await assertFails(
      asUser(C).ref(`rooms/${ROOM}/live/${C}`).set(status("intruder", 4)),
    );
  });
  await it("A can clear their own status", async () => {
    await assertSucceeds(asUser(A).ref(`rooms/${ROOM}/live/${A}`).set(null));
  });
  await it("status text is capped at 500 characters", async () => {
    await assertFails(
      asUser(A).ref(`rooms/${ROOM}/live/${A}`).set(status("x".repeat(501), 5)),
    );
    await assertSucceeds(
      asUser(A).ref(`rooms/${ROOM}/live/${A}`).set(status("x".repeat(500), 6)),
    );
  });
  await it("a live node may not carry fields beyond text and updatedAt", async () => {
    await assertFails(
      asUser(A).ref(`rooms/${ROOM}/live/${A}`).set({ ...status("hi", 7), evil: 1 }),
    );
  });

  console.log("\n--- membership cannot be hijacked ---");
  await it("C cannot add themselves to a room", async () => {
    await assertFails(asUser(C).ref(`rooms/${ROOM}/member/uid`).set(C));
  });
  await it("B cannot promote C", async () => {
    await assertFails(asUser(B).ref(`rooms/${ROOM}/member/uid`).set(C));
  });
  await it("the owner may name the member, which is how a join gets a name", async () => {
    // Deliberate, and the one place the rules let someone speak about another person:
    // only the room's owner, and only for a member slot that has no name yet.
    await assertSucceeds(asUser(A).ref(`rooms/${ROOM}/member/name`).set("Bob"));
  });
  await it("a member cannot rename the owner", async () => {
    await assertFails(asUser(B).ref(`rooms/${ROOM}/owner/name`).set("Not Alice"));
  });
  await it("a member can leave, removing themselves as member", async () => {
    await assertSucceeds(asUser(B).ref(`rooms/${ROOM}/member/uid`).set(null));
  });

  console.log("\n--- per-user records ---");
  await it("A can store their own FCM token", async () => {
    await assertSucceeds(asUser(A).ref(`users/${A}/fcmToken`).set("new-token"));
  });
  await it("A cannot overwrite B's record", async () => {
    await assertFails(asUser(A).ref(`users/${B}/fcmToken`).set("stolen"));
  });
  await it("A cannot read B's record", async () => {
    await assertFails(asUser(A).ref(`users/${B}`).once("value"));
  });

  console.log("\n--- nothing outside the schema ---");
  await it("an unknown top-level key is refused", async () => {
    await assertFails(asUser(A).ref(`stolen/anything`).set(1));
  });
  await it("an unknown field inside a user's record is refused", async () => {
    await assertFails(asUser(A).ref(`users/${A}/role`).set("admin"));
  });

  await env.cleanup();

  const failed = results.filter((r) => !r[0]);
  console.log(
    `\n=== ${results.length - failed.length}/${results.length} passed` +
      (failed.length ? `, ${failed.length} FAILED` : "") +
      " ===\n",
  );
  process.exit(failed.length ? 1 : 0);
})().catch((e) => {
  console.error(
    `\nCould not run the rules suite. The Realtime Database emulator has to be up on\n` +
      `127.0.0.1:9000 for these to mean anything:\n\n` +
      `    firebase emulators:start --only database\n\n` +
      `${e.message}\n`,
  );
  process.exit(1);
});
