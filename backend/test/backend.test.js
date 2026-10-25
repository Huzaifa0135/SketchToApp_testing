import test from "node:test";
import assert from "node:assert/strict";
import crypto from "node:crypto";

function isSafeRelativePath(relPath) {
  if (!relPath || relPath.startsWith("/") || relPath.startsWith("\\")) return false;
  if (relPath.includes("..") || relPath.includes("\0")) return false;
  return /^[a-zA-Z0-9_./-]+$/.test(relPath);
}

function sanitizeBuildLogs(rawLogs, secrets) {
  let out = rawLogs;
  for (const s of secrets) {
    if (s && s.length >= 4) {
      out = out.split(s).join("[REDACTED]");
    }
  }
  return out.replace(/\/tmp\/worker_[a-zA-Z0-9_-]+/g, "/workspace");
}

test("rejects path traversal and absolute archive entries", () => {
  assert.equal(isSafeRelativePath("lib/main.dart"), true);
  assert.equal(isSafeRelativePath("lib/screens/home_screen.dart"), true);
  assert.equal(isSafeRelativePath("../etc/passwd"), false);
  assert.equal(isSafeRelativePath("/etc/shadow"), false);
  assert.equal(isSafeRelativePath("lib/../../secret.txt"), false);
});

test("redacts worker secrets and internal host paths from build logs", () => {
  const raw = "Running in /tmp/worker_99ab12 with token SECRET_TOKEN_XYZ123";
  const clean = sanitizeBuildLogs(raw, ["SECRET_TOKEN_XYZ123"]);
  assert.equal(clean.includes("SECRET_TOKEN_XYZ123"), false);
  assert.equal(clean.includes("/tmp/worker_99ab12"), false);
  assert.equal(clean, "Running in /workspace with token [REDACTED]");
});

test("verifies deterministic SHA-256 artifact integrity", () => {
  const sampleApkBytes = Buffer.from("PK\x03\x04verified-flutter-apk-payload");
  const digest = crypto.createHash("sha256").update(sampleApkBytes).digest("hex");
  assert.equal(digest.length, 64);
  const tampered = Buffer.from("PK\x03\x04tampered-payload");
  const tamperedDigest = crypto.createHash("sha256").update(tampered).digest("hex");
  assert.notEqual(digest, tamperedDigest);
});
