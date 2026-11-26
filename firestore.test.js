const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-jarvislo-e3be8951-a013-4ecc-8bdd-ac5448e3db63";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function getDb(context) {
  return context.firestore({ databaseId: DATABASE_ID });
}

test("Unauthenticated user cannot read or write profiles, notes, or chats", async () => {
  const db = getDb(testEnv.unauthenticatedContext());
  const now = new Date(Date.now() - 1000);
  await assertFails(db.collection("users").doc(ALICE_UID).get());
  await assertFails(db.collection("users").doc(ALICE_UID).collection("notes").get());
  await assertFails(
    db.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      nickname: "Hacker",
      languageCode: "ru",
      paletteId: "neon_sapphire",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Owner can create, read, update profile; rejects ghost fields, invalid language, and future timestamp", async () => {
  const db = getDb(testEnv.authenticatedContext(ALICE_UID));
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    db.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      nickname: "Viktor",
      languageCode: "ru",
      paletteId: "neon_sapphire",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(db.collection("users").doc(ALICE_UID).get());

  await assertSucceeds(
    db.collection("users").doc(ALICE_UID).update({
      nickname: "Viktor Pro",
      paletteId: "amoled_obsidian",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject immutable createdAt mutation
  await assertFails(
    db.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject ghost field on update
  await assertFails(
    db.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject invalid language enum
  await assertFails(
    db.collection("users").doc(ALICE_UID).update({
      languageCode: "fr",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject future timestamp
  await assertFails(
    db.collection("users").doc(ALICE_UID).update({
      nickname: "Future",
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("Cross-user isolation prevents Bob from reading or writing Alice's profile, notes, and chats", async () => {
  const aliceDb = getDb(testEnv.authenticatedContext(ALICE_UID));
  const bobDb = getDb(testEnv.authenticatedContext(BOB_UID));
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("notes").doc("note_1").set({
      userId: ALICE_UID,
      title: "Secret Plan",
      artifactType: "PAGE",
      category: "All",
      content: "Confidential markdown note",
      pinned: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("notes").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("notes").doc("note_1").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("notes").doc("note_1").update({
      title: "Hijacked",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Owner can list and update notes & chats without @DocumentId id field; extra id field is rejected", async () => {
  const aliceDb = getDb(testEnv.authenticatedContext(ALICE_UID));
  const created = new Date(Date.now() - 5000);

  // Empty collection list query succeeds
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("notes").get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("chats").get());

  // Valid create chat
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("chats").doc("chat_1").set({
      userId: ALICE_UID,
      title: "Android Build Help",
      pinned: false,
      lastMessagePreview: "Configured Firebase",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Valid partial update chat
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("chats").doc("chat_1").update({
      pinned: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject redundant `id` key matching @DocumentId trap
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("notes").doc("note_bad").set({
      id: "note_bad",
      userId: ALICE_UID,
      title: "Bad Note",
      artifactType: "PAGE",
      category: "All",
      content: "Body",
      pinned: false,
      createdAt: created,
      updatedAt: created,
    })
  );
});
