const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

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

test("Unauthenticated user cannot read user profile", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
});

test("Alice can create and read her own profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const aliceDoc = aliceDb.collection("users").doc(ALICE_UID);
  await assertSucceeds(
    aliceDoc.set({
      userId: ALICE_UID,
      displayName: "Alice Trader",
      accountBalance: 25000.0,
      accountCurrency: "USD",
      defaultRiskPercent: 0.5,
      maxDailyLoss: 300.0,
      maxOpenRisk: 500.0,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(aliceDoc.get());
});

test("Bob cannot read or write Alice's profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await aliceDb.collection("users").doc(ALICE_UID).set({
    userId: ALICE_UID,
    displayName: "Alice",
    createdAt: new Date(),
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).set({
      userId: BOB_UID,
      createdAt: new Date(),
    })
  );
});

test("Alice can create and list signals in her signals subcollection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const signalDoc = aliceDb.collection("users").doc(ALICE_UID).collection("signals").doc("sig_1");
  await assertSucceeds(
    signalDoc.set({
      userId: ALICE_UID,
      symbol: "XAU/USD",
      direction: "BUY",
      entry: 2650.5,
      stopLoss: 2640.0,
      takeProfit1: 2665.0,
      takeProfit2: 2680.0,
      takeProfit3: 2700.0,
      riskReward: 1.5,
      setupQualityScore: 85,
      profileShape: "P-SHAPED",
      marketRegime: "TRENDING",
      createdAt: new Date(),
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("signals").get());
});

test("Bob cannot access Alice's signals", async () => {
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("signals").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("signals").doc("sig_1").get()
  );
});
