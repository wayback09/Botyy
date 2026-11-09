const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const CHARLIE_UID = "charlie_789";

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

// --- SECURITY BOUNDS TESTS ---

test("Unauthenticated user: cannot read users", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").get());
});

test("Authenticated user: can create their own profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      username: "alice",
      displayName: "Alice Smith",
      createdAt: new Date(),
    })
  );
});

test("Authenticated user: cannot create another user's profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).set({
      uid: BOB_UID,
      username: "bob",
      displayName: "Bob Jones",
      createdAt: new Date(),
    })
  );
});

test("Conversations: member can read and post messages, non-member is denied", async () => {
  // Seed conversation with Alice and Bob as members
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("conversations").doc("convo_ab").set({
      conversationId: "convo_ab",
      type: "DIRECT",
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: new Date(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  // Alice can read the conversation
  await assertSucceeds(aliceDb.collection("conversations").doc("convo_ab").get());
  // Charlie (non-member) cannot read the conversation
  await assertFails(charlieDb.collection("conversations").doc("convo_ab").get());

  // Alice can send message in conversation
  await assertSucceeds(
    aliceDb.collection("conversations").doc("convo_ab").collection("messages").doc("msg_1").set({
      messageId: "msg_1",
      conversationId: "convo_ab",
      senderUid: ALICE_UID,
      content: "Hello Bob!",
      createdAt: new Date(),
    })
  );

  // Charlie cannot send message in conversation
  await assertFails(
    charlieDb.collection("conversations").doc("convo_ab").collection("messages").doc("msg_2").set({
      messageId: "msg_2",
      conversationId: "convo_ab",
      senderUid: CHARLIE_UID,
      content: "Intruder message",
      createdAt: new Date(),
    })
  );
});

test("Channels: only owner can broadcast post", async () => {
  // Seed channel owned by Alice
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("channels").doc("channel_1").set({
      channelId: "channel_1",
      ownerUid: ALICE_UID,
      name: "Alice Broadcasts",
      description: "Official updates",
      createdAt: new Date(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Alice (owner) can post broadcast
  await assertSucceeds(
    aliceDb.collection("channels").doc("channel_1").collection("posts").doc("cp_1").set({
      postId: "cp_1",
      channelId: "channel_1",
      authorUid: ALICE_UID,
      content: "Big announcement!",
      createdAt: new Date(),
    })
  );

  // Bob (not owner) cannot post broadcast
  await assertFails(
    bobDb.collection("channels").doc("channel_1").collection("posts").doc("cp_2").set({
      postId: "cp_2",
      channelId: "channel_1",
      authorUid: BOB_UID,
      content: "Unauthorized post",
      createdAt: new Date(),
    })
  );
});

test("Blocks: user can create block record", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("blocks").doc(`${ALICE_UID}_${BOB_UID}`).set({
      blockerUid: ALICE_UID,
      blockedUid: BOB_UID,
      createdAt: new Date(),
    })
  );

  // Bob cannot create block pretending to be Alice
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("blocks").doc(`${ALICE_UID}_${CHARLIE_UID}`).set({
      blockerUid: ALICE_UID,
      blockedUid: CHARLIE_UID,
      createdAt: new Date(),
    })
  );
});
