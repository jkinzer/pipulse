const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_user_001";
const BOB_UID = "bob_user_002";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
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

function validUserPayload(uid, ts = new Date(Date.now() - 2000)) {
  return {
    userId: uid,
    displayName: "Alex Rivera",
    email: "alex@example.com",
    activeDeviceId: "dev_primary",
    refreshIntervalSeconds: 5,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validDevicePayload(uid, ts = new Date(Date.now() - 2000)) {
  return {
    userId: uid,
    name: "Rack Primary Pi-hole",
    host: "192.168.1.2",
    port: 80,
    useHttps: false,
    apiVersion: "V6",
    basePath: "",
    locationTag: "Home LAN",
    notes: "Raspberry Pi 5 on eth0",
    hasStoredCredential: true,
    lastConnectedAtMs: 0,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validAuditPayload(uid, ts = new Date(Date.now() - 2000)) {
  return {
    userId: uid,
    deviceId: "dev_primary",
    deviceName: "Rack Primary Pi-hole",
    actionType: "WHITELIST_ADD",
    target: "api.github.com",
    createdAt: ts,
  };
}

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID))
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("devices").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("devices")
      .doc("dev_1")
      .set(validDevicePayload(ALICE_UID))
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("auditLogs").get()
  );
});

test("User profile: owner create, partial updates, and strict key/immutability guards", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID, created))
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());

  // Listing /users is prohibited to protect PII
  await assertFails(aliceDb.collection("users").get());

  // Partial updates matching Kotlin UserRepository
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      activeDeviceId: "dev_secondary",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      refreshIntervalSeconds: 10,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject future timestamp, ghost field, out-of-range interval, and createdAt mutation
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      refreshIntervalSeconds: 999,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Cross-user isolation
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      activeDeviceId: "hijack",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Devices subcollection: owner CRUD, empty list query, and security bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Empty subcollection list must succeed for owner
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("devices").get()
  );

  // Create device (no @DocumentId `id` key)
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("devices")
      .doc("dev_primary")
      .set(validDevicePayload(ALICE_UID, created))
  );

  // Reject extra `id` field or ghost field on create
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("devices")
      .doc("dev_extra")
      .set({
        ...validDevicePayload(ALICE_UID, created),
        id: "dev_extra",
      })
  );

  // Reject invalid API version or port
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("devices")
      .doc("dev_bad_ver")
      .set({
        ...validDevicePayload(ALICE_UID, created),
        apiVersion: "V4",
      })
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("devices")
      .doc("dev_bad_port")
      .set({
        ...validDevicePayload(ALICE_UID, created),
        port: 70000,
      })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("devices")
      .doc("dev_future")
      .set(validDevicePayload(ALICE_UID, new Date(Date.now() + 60000)))
  );

  // Partial updates matching Kotlin PiholeDeviceRepository
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("devices")
      .doc("dev_primary")
      .update({
        lastConnectedAtMs: Date.now(),
        updatedAt: new Date(Date.now() - 1000),
      })
  );
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("devices")
      .doc("dev_primary")
      .update({
        name: "Primary VPN Pi-hole",
        host: "pihole.internal.example",
        port: 443,
        useHttps: true,
        apiVersion: "V6",
        basePath: "",
        locationTag: "WireGuard VPN",
        notes: "TLS enabled",
        hasStoredCredential: true,
        updatedAt: new Date(Date.now() - 500),
      })
  );

  // Cross-user isolation
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("devices").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("devices")
      .doc("dev_primary")
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("devices")
      .doc("dev_primary")
      .delete()
  );

  // Owner delete succeeds
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("devices")
      .doc("dev_primary")
      .delete()
  );
});

test("Audit logs subcollection: append-only, owner-scoped, immutable", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("auditLogs")
      .orderBy("createdAt", "desc")
      .limit(50)
      .get()
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("auditLogs")
      .doc("log_001")
      .set(validAuditPayload(ALICE_UID))
  );

  // Updates to audit logs must fail (immutable)
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("auditLogs")
      .doc("log_001")
      .update({ target: "tampered.com" })
  );

  // Invalid actionType fails
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("auditLogs")
      .doc("log_002")
      .set({
        ...validAuditPayload(ALICE_UID),
        actionType: "UNKNOWN_ACTION",
      })
  );

  // Cross-user access fails
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("auditLogs").get()
  );

  // Owner delete succeeds
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("auditLogs")
      .doc("log_001")
      .delete()
  );
});
