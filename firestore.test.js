const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_advocate_01";
const BOB_UID = "bob_responder_02";
const CAROL_UID = "carol_outsider_03";
const ADMIN_EMAIL = "suryaprakash2088@gmail.com";

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

function nowPast() {
  return new Date(Date.now() - 2000);
}

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("users").doc(ALICE_UID).get());
  await assertFails(unauth.collection("sos_incidents").where("broadcastActive", "==", true).get());
  await assertFails(unauth.collection("feed_posts").where("moderationStatus", "==", "PUBLISHED").where("visibility", "==", "NETWORK").get());
  await assertFails(unauth.collection("legal_updates").where("reviewStatus", "==", "PUBLISHED").get());
});

test("User profile and emergency contacts: owner create/update/list succeed; cross-user and self-approve fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = nowPast();

  // Valid create
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Adv. Alice Sharma",
      email: "alice@bar.example",
      platformId: "ADV-ALIC01",
      city: "New Delhi",
      state: "Delhi",
      courtLocation: "Supreme Court of India",
      practiceAreas: ["Constitutional", "Criminal"],
      yearsOfPractice: 7,
      bio: "Advocate-on-Record",
      verificationStatus: "NOT_SUBMITTED",
      e2eePublicKeyB64: "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEtest",
      e2eeKeyFingerprint: "AB:CD:EF:12",
      nearbyOptIn: true,
      locationConsent: true,
      discoverable: true,
      feedPublic: true,
      premiumActive: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Self-approving verificationStatus must fail for non-admin
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      verificationStatus: "APPROVED",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Submitting verification succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      barCouncil: "Bar Council of Delhi",
      enrolmentNumberMasked: "D/****/2019",
      enrolmentDate: "2019-07-14",
      evidenceDocName: "enrolment_cert.jpg",
      verificationStatus: "SUBMITTED",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Cross-user read on private /users/{uid} must fail
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());

  // Unverified email spoofing admin must fail
  const spoofAdminDb = testEnv.authenticatedContext("spoof_uid", {
    email: ADMIN_EMAIL,
    email_verified: false,
  }).firestore();
  await assertFails(
    spoofAdminDb.collection("users").doc(ALICE_UID).update({
      verificationStatus: "APPROVED",
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Verified admin can approve
  const realAdminDb = testEnv.authenticatedContext("admin_uid", {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();
  await assertSucceeds(
    realAdminDb.collection("users").doc(ALICE_UID).update({
      verificationStatus: "APPROVED",
      verificationNote: "Manual document check complete",
      updatedAt: new Date(Date.now() - 300),
    })
  );

  // Emergency contacts subcollection: owner works, cross-user fails
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("emergency_contacts").doc("c1").set({
      userId: ALICE_UID,
      name: "Senior Counsel Rao",
      phoneNumber: "+919810011223",
      relationship: "Chamber Senior",
      enabled: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("emergency_contacts").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("emergency_contacts").get()
  );
});

test("SOS incident lifecycle, responder coordination, and E2EE encrypted messages", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const carolDb = testEnv.authenticatedContext(CAROL_UID).firestore();
  const ts = nowPast();
  const incidentId = "sos_inc_001";

  // Alice creates SOS incident
  await assertSucceeds(
    aliceDb.collection("sos_incidents").doc(incidentId).set({
      ownerUserId: ALICE_UID,
      ownerName: "Adv. Alice Sharma",
      ownerPlatformId: "ADV-ALIC01",
      ownerVerified: true,
      idempotencyKey: "idem_key_12345678",
      category: "A",
      state: "ACTIVE",
      description: "Urgent assistance outside District Court Gate 3",
      coarseArea: "Tis Hazari Court Complex, Delhi",
      lat: 28.6685,
      lng: 77.2134,
      accuracyM: 12.5,
      locationShared: true,
      broadcastActive: true,
      currentRadiusKm: 2,
      alertedCount: 4,
      smsChannelStatus: "SUBMITTED",
      pushChannelStatus: "SUBMITTED",
      responderUids: [],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob queries active broadcasts (aligned with allow list)
  await assertSucceeds(
    bobDb.collection("sos_incidents").where("broadcastActive", "==", true).get()
  );
  // Unfiltered list query on sos_incidents must fail
  await assertFails(carolDb.collection("sos_incidents").get());

  // Bob joins as responder
  await assertSucceeds(
    bobDb.collection("sos_incidents").doc(incidentId).update({
      responderUids: [BOB_UID],
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    bobDb.collection("sos_incidents").doc(incidentId).collection("responders").doc(BOB_UID).set({
      incidentId,
      ownerUserId: ALICE_UID,
      responderUserId: BOB_UID,
      responderName: "Adv. Bob Verma",
      responderPlatformId: "ADV-BOB002",
      status: "ACCEPTED",
      note: "Heading to Gate 3",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob updates responder status to ON_THE_WAY
  await assertSucceeds(
    bobDb.collection("sos_incidents").doc(incidentId).collection("responders").doc(BOB_UID).update({
      status: "ON_THE_WAY",
      updatedAt: new Date(Date.now() - 800),
    })
  );

  // Alice sends E2EE encrypted message
  await assertSucceeds(
    aliceDb.collection("sos_incidents").doc(incidentId).collection("messages").doc("msg_1").set({
      incidentId,
      ownerUserId: ALICE_UID,
      participantUids: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      senderName: "Adv. Alice Sharma",
      ciphertextB64: "ZW5jcnlwdGVkX2J5dGVzX2hlcmU=",
      ivB64: "MTIzNDU2Nzg5MDEy",
      senderPublicKeyB64: "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEtest",
      senderKeyFingerprint: "AB:CD:EF:12",
      protocolVersion: "ECDH-P256-AES256-GCM-v1",
      wrappedKeys: {
        [ALICE_UID]: "iv1.wrapped1",
        [BOB_UID]: "iv2.wrapped2",
      },
      messageType: "TEXT",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob can query messages where participantUids contains BOB_UID
  await assertSucceeds(
    bobDb.collection("sos_incidents").doc(incidentId).collection("messages")
      .where("participantUids", "array-contains", BOB_UID)
      .get()
  );

  // Carol (non-participant) cannot read encrypted messages or query Alice's messages or send one
  await assertFails(
    carolDb.collection("sos_incidents").doc(incidentId).collection("messages").doc("msg_1").get()
  );
  await assertFails(
    carolDb.collection("sos_incidents").doc(incidentId).collection("messages")
      .where("participantUids", "array-contains", ALICE_UID)
      .get()
  );
  await assertFails(
    carolDb.collection("sos_incidents").doc(incidentId).collection("messages").doc("msg_2").set({
      incidentId,
      ownerUserId: ALICE_UID,
      participantUids: [ALICE_UID, CAROL_UID],
      senderId: CAROL_UID,
      senderName: "Carol",
      ciphertextB64: "ZmFrZQ==",
      ivB64: "MTIzNDU2Nzg5MDEy",
      senderPublicKeyB64: "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEfake",
      senderKeyFingerprint: "00:11:22:33",
      protocolVersion: "ECDH-P256-AES256-GCM-v1",
      wrappedKeys: { [CAROL_UID]: "x.y" },
      messageType: "TEXT",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Alice resolves incident; subsequent owner updates fail (terminal state locked)
  await assertSucceeds(
    aliceDb.collection("sos_incidents").doc(incidentId).update({
      state: "RESOLVED",
      broadcastActive: false,
      closureReason: "Safe with responder assistance",
      resolvedAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection("sos_incidents").doc(incidentId).update({
      description: "Tamper after close",
      updatedAt: new Date(Date.now() - 200),
    })
  );
});

test("Feed posts and advocate directory queries match security rules", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = nowPast();

  await assertSucceeds(
    aliceDb.collection("advocate_directory").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Adv. Alice Sharma",
      platformId: "ADV-ALIC01",
      city: "New Delhi",
      state: "Delhi",
      courtLocation: "Supreme Court of India",
      practiceAreas: ["Constitutional"],
      verificationStatus: "NOT_SUBMITTED",
      nearbyOptIn: true,
      discoverable: true,
      e2eePublicKeyB64: "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEtest",
      e2eeKeyFingerprint: "AB:CD:EF:12",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    bobDb.collection("advocate_directory").where("nearbyOptIn", "==", true).get()
  );

  await assertSucceeds(
    aliceDb.collection("feed_posts").doc("post_1").set({
      authorId: ALICE_UID,
      authorName: "Adv. Alice Sharma",
      authorPlatformId: "ADV-ALIC01",
      authorVerified: false,
      courtTag: "Delhi High Court",
      city: "New Delhi",
      body: "Reminder: Gate 5 Bar Association helpdesk is open until 6 PM today.",
      visibility: "NETWORK",
      likeCount: 0,
      commentCount: 0,
      likedByUids: [],
      reportedByUids: [],
      moderationStatus: "PUBLISHED",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    bobDb.collection("feed_posts")
      .where("moderationStatus", "==", "PUBLISHED")
      .where("visibility", "==", "NETWORK")
      .get()
  );
});

test("Dedicated sos_alerts and safety_monitoring collections enforce owner and active-alert rules", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = nowPast();

  // Safety monitoring: Alice can write & read her own live session; Bob cannot
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("safety_monitoring").doc("live").set({
      userId: ALICE_UID,
      active: true,
      lat: 28.6139,
      lng: 77.2090,
      accuracyM: 9.5,
      locationSource: "FusedForegroundService",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("safety_monitoring").doc("live").get()
  );

  // Dedicated sos_alerts collection: Alice creates immediate alert; Bob can query ACTIVE alerts
  await assertSucceeds(
    aliceDb.collection("sos_alerts").doc("alert_01").set({
      ownerUserId: ALICE_UID,
      ownerName: "Adv. Alice Sharma",
      ownerPlatformId: "ADV-ALIC01",
      category: "A",
      status: "ACTIVE",
      message: "Immediate SOS trigger",
      lat: 28.6229,
      lng: 77.2392,
      accuracyM: 6.2,
      locationSource: "PlayServicesFusedGPS",
      monitoringActive: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    bobDb.collection("sos_alerts").where("status", "==", "ACTIVE").get()
  );
  await assertFails(
    bobDb.collection("sos_alerts").doc("alert_01").update({
      status: "RESOLVED",
      updatedAt: new Date(Date.now() - 300),
    })
  );
  await assertSucceeds(
    aliceDb.collection("sos_alerts").doc("alert_01").update({
      status: "RESOLVED",
      monitoringActive: false,
      updatedAt: new Date(Date.now() - 200),
    })
  );
});
