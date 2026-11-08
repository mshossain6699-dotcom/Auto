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

function validWorkflowPayload(userId = ALICE_UID, overrides = {}) {
  const now = new Date();
  return {
    userId,
    title: "Daily Morning Battery & App Audit",
    description: "Checks battery level, opens system settings, and captures OCR snapshot.",
    triggerType: "SCHEDULED",
    scheduleCron: "08:00 Daily",
    isEnabled: true,
    stepsJson: JSON.stringify([{ type: "OPEN_APP", target: "com.android.settings" }]),
    stepCount: 1,
    runCount: 0,
    lastStatus: "IDLE",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validLogPayload(userId = ALICE_UID, overrides = {}) {
  return {
    userId,
    workflowId: "wf_1",
    workflowTitle: "Daily Morning Battery & App Audit",
    status: "SUCCESS",
    summary: "Executed 3 steps without errors.",
    commandOutput: "> getprop ro.build.version.release\n16",
    durationMs: 420,
    createdAt: new Date(),
    ...overrides,
  };
}

function validTelemetryPayload(userId = ALICE_UID, overrides = {}) {
  return {
    userId,
    deviceModel: "Google Pixel 9 Pro",
    androidVersion: "Android 16",
    cpuUsagePercent: 24,
    batteryPercent: 88,
    batteryTempTenthsC: 305,
    ramUsedMb: 4200,
    ramTotalMb: 8192,
    networkLatencyMs: 22,
    networkTransport: "Wi-Fi",
    createdAt: new Date(),
    ...overrides,
  };
}

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

test("Unauthenticated user: cannot read or create workflows", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("workflows").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("workflows").doc("wf_1").set(validWorkflowPayload())
  );
});

test("Authenticated owner: can create, read, list, update, and delete own workflow", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("workflows").doc("wf_1");

  await assertSucceeds(docRef.set(validWorkflowPayload(ALICE_UID)));
  await assertSucceeds(docRef.get());
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("workflows").where("userId", "==", ALICE_UID).get()
  );
  await assertSucceeds(
    docRef.update({
      title: "Updated Morning Audit",
      runCount: 1,
      lastStatus: "SUCCESS",
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(docRef.delete());
});

test("Cross-user isolation: Bob cannot read, list, update, or delete Alice's workflow", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("users")
      .doc(ALICE_UID)
      .collection("workflows")
      .doc("wf_1")
      .set(validWorkflowPayload(ALICE_UID));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aliceDocFromBob = bobDb.collection("users").doc(ALICE_UID).collection("workflows").doc("wf_1");

  await assertFails(aliceDocFromBob.get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("workflows").get());
  await assertFails(aliceDocFromBob.update({ title: "Hijacked", updatedAt: new Date() }));
  await assertFails(aliceDocFromBob.delete());
});

test("Shadow update & value poisoning: rejects ghost fields and invalid enum/bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("workflows").doc("wf_1");
  await assertSucceeds(docRef.set(validWorkflowPayload(ALICE_UID)));

  // Ghost field on create
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("workflows")
      .doc("wf_2")
      .set(validWorkflowPayload(ALICE_UID, { isVerifiedAdmin: true }))
  );

  // Invalid enum on update
  await assertFails(docRef.update({ triggerType: "HACKED_TRIGGER", updatedAt: new Date() }));

  // Immutable userId mutation on update
  await assertFails(docRef.update({ userId: BOB_UID, updatedAt: new Date() }));
});

test("Execution logs: owner can create, list, and delete logs, but cannot update logs", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const logRef = aliceDb.collection("users").doc(ALICE_UID).collection("logs").doc("log_1");

  await assertSucceeds(logRef.set(validLogPayload(ALICE_UID)));
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("logs").where("userId", "==", ALICE_UID).get()
  );
  await assertFails(logRef.update({ summary: "Tampered log" }));
  await assertSucceeds(logRef.delete());
});

test("Synced telemetry logs: owner can create, list, and delete telemetry logs, cross-user denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const telRef = aliceDb.collection("users").doc(ALICE_UID).collection("telemetry_logs").doc("tel_1");

  await assertSucceeds(telRef.set(validTelemetryPayload(ALICE_UID)));
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("telemetry_logs").where("userId", "==", ALICE_UID).get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("telemetry_logs").doc("tel_1").get()
  );
  await assertFails(telRef.update({ cpuUsagePercent: 99 }));
  await assertSucceeds(telRef.delete());
});
