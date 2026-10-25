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

function serverTs() {
  return new Date(Date.now() - 1000);
}

async function seedProject(projectId, ownerUid) {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const now = new Date();
    await db.collection("projects").doc(projectId).set({
      userId: ownerUid,
      name: "Demo Project",
      description: "Seeded project",
      packageName: "com.generated.demo",
      sourceType: "sketch",
      stage: "draft",
      screenCount: 1,
      schemaVersion: 1,
      createdAt: now,
      updatedAt: now,
    });
  });
}

// --- DIRTY DOZEN & SECURITY BOUNDARY TESTS ---

test("1. Unauthenticated caller cannot read or list projects", async () => {
  await seedProject("proj_1", ALICE_UID);
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("projects").doc("proj_1").get());
  await assertFails(unauthDb.collection("projects").where("userId", "==", ALICE_UID).get());
});

test("2. Cross-user project read, update, and delete are rejected", async () => {
  await seedProject("proj_alice", ALICE_UID);
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("projects").doc("proj_alice").get());
  await assertFails(bobDb.collection("projects").doc("proj_alice").delete());
});

test("3. Unfiltered list scraping on projects fails (rules are not filters)", async () => {
  await seedProject("proj_alice", ALICE_UID);
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("projects").get());
  await assertSucceeds(aliceDb.collection("projects").where("userId", "==", ALICE_UID).get());
});

test("4. PII isolation on /users: list is forbidden and cross-user get is forbidden", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const now = new Date();
    await ctx.firestore().collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      planTier: "free",
      onboardingCompleted: true,
      aiCallsUsed: 0,
      buildsUsed: 0,
      createdAt: now,
      updatedAt: now,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").where("userId", "==", BOB_UID).get());

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
});

test("5. Self-assigned paid plan privilege escalation on user profile create fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = serverTs(aliceDb);
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      planTier: "enterprise",
      onboardingCompleted: false,
      aiCallsUsed: 0,
      buildsUsed: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      planTier: "free",
      onboardingCompleted: false,
      aiCallsUsed: 0,
      buildsUsed: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("6. Shadow field injection on project create and update fails", async () => {
  await seedProject("proj_1", ALICE_UID);
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = serverTs(aliceDb);

  await assertFails(
    aliceDb.collection("projects").doc("proj_1").update({
      name: "Renamed",
      isVerifiedAdmin: true,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("projects").doc("proj_1").update({
      name: "Renamed Cleanly",
      updatedAt: ts,
    })
  );
});

test("7. Parent Gate blocks orphaned screen creation or attaching screen to another user's project", async () => {
  await seedProject("bob_proj", BOB_UID);
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = serverTs(aliceDb);

  // Orphaned project
  await assertFails(
    aliceDb.collection("screens").doc("scr_orphan").set({
      userId: ALICE_UID,
      projectId: "nonexistent_proj",
      name: "Home",
      route: "/home",
      isInitial: true,
      orderIndex: 0,
      components: [],
      warnings: [],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Cross-user parent project
  await assertFails(
    aliceDb.collection("screens").doc("scr_hijack").set({
      userId: ALICE_UID,
      projectId: "bob_proj",
      name: "Home",
      route: "/home",
      isInitial: true,
      orderIndex: 0,
      components: [],
      warnings: [],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Valid owned parent project succeeds
  await seedProject("alice_proj", ALICE_UID);
  await assertSucceeds(
    aliceDb.collection("screens").doc("scr_valid").set({
      userId: ALICE_UID,
      projectId: "alice_proj",
      name: "Home",
      route: "/home",
      isInitial: true,
      orderIndex: 0,
      components: [{ id: "cmp_1", type: "BUTTON", label: "Continue" }],
      warnings: [],
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("8. Terminal state locking prevents mutating succeeded/failed build_jobs", async () => {
  await seedProject("alice_proj", ALICE_UID);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const now = new Date();
    await ctx.firestore().collection("build_jobs").doc("job_done").set({
      userId: ALICE_UID,
      projectId: "alice_proj",
      generationJobId: "gen_1",
      buildProfile: "debug",
      status: "failed",
      workerMode: "unconfigured",
      flutterVersion: "3.27.0",
      exitCode: 1,
      sanitizedLogs: "Worker endpoint not configured",
      artifactId: "",
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = serverTs(aliceDb);
  await assertFails(
    aliceDb.collection("build_jobs").doc("job_done").update({
      status: "succeeded",
      updatedAt: ts,
    })
  );
});

test("9. Append-only /usage_logs allows owner create and filtered list, blocks update and delete", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = serverTs(aliceDb);

  await assertSucceeds(
    aliceDb.collection("usage_logs").doc("log_1").set({
      userId: ALICE_UID,
      actionType: "sketch_analysis",
      projectId: "alice_proj",
      status: "succeeded",
      createdAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("usage_logs").where("userId", "==", ALICE_UID).get()
  );

  await assertFails(
    aliceDb.collection("usage_logs").doc("log_1").update({ status: "failed" })
  );
  await assertFails(
    aliceDb.collection("usage_logs").doc("log_1").delete()
  );
});

test("10. Application documents, designs, assets, generation_jobs, and generated_apps enforce parent gate and ownership", async () => {
  await seedProject("alice_proj", ALICE_UID);
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = serverTs(aliceDb);

  await assertSucceeds(
    aliceDb.collection("application_documents").doc("alice_proj").set({
      userId: ALICE_UID,
      projectId: "alice_proj",
      appName: "My Sketch App",
      initialScreenId: "scr_1",
      schemaVersion: 1,
      primaryColorHex: "#58E6D0",
      accentColorHex: "#8B7CF6",
      backgroundColorHex: "#0B1118",
      surfaceColorHex: "#16212A",
      fontFamily: "Inter",
      darkMode: true,
      borderRadiusDp: 12,
      screenOrder: ["scr_1"],
      warnings: [],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("designs").doc("des_1").set({
      userId: ALICE_UID,
      projectId: "alice_proj",
      sourceType: "sketch",
      sourceReference: "local://sketches/sketch_1.jpg",
      analysisStatus: "completed",
      schemaVersion: 1,
      warningCount: 0,
      warningsSummary: "",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("generation_jobs").doc("gen_1").set({
      userId: ALICE_UID,
      projectId: "alice_proj",
      sourceSchemaVersion: 1,
      status: "generating",
      fileCount: 10,
      diagnosticsSummary: "All checks passed",
      zipStorageRef: "exports/alice_proj.zip",
      zipChecksumSha256: "abc123",
      createdAt: ts,
      updatedAt: ts,
    })
  );
});
