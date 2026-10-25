import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import { execFile } from "node:child_process";
import { promisify } from "node:util";

const execFileAsync = promisify(execFile);
const PORT = Number(process.env.PORT ?? 8090);
const WORK_ROOT = path.resolve("/opt/sketch-worker/work");
const SHARED_SECRET = process.env.BUILD_WORKER_SHARED_SECRET ?? "";
const STEP_TIMEOUT_MS = 180_000; // 3 min strict timeout per step

fs.mkdirSync(WORK_ROOT, { recursive: true });

function sanitizeOutput(raw, jobDir) {
  let clean = String(raw ?? "");
  if (SHARED_SECRET) {
    clean = clean.split(SHARED_SECRET).join("[REDACTED]");
  }
  return clean.split(jobDir).join("/workspace").slice(-16_000);
}

async function runStep(cmd, args, cwd, logs, jobDir) {
  logs.push(`$ ${cmd} ${args.join(" ")}`);
  const { stdout, stderr } = await execFileAsync(cmd, args, {
    cwd,
    timeout: STEP_TIMEOUT_MS,
    maxBuffer: 4 * 1024 * 1024,
    env: {
      PATH: process.env.PATH,
      HOME: process.env.HOME,
      ANDROID_SDK_ROOT: process.env.ANDROID_SDK_ROOT ?? "/opt/android-sdk-linux",
      CI: "true"
    }
  });
  if (stdout) logs.push(sanitizeOutput(stdout, jobDir));
  if (stderr) logs.push(sanitizeOutput(stderr, jobDir));
}

const server = http.createServer(async (req, res) => {
  if (req.method === "GET" && req.url === "/healthz") {
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ status: "ok", worker: "isolated-flutter-3.27" }));
    return;
  }

  if (req.method !== "POST" || req.url !== "/compile") {
    res.writeHead(404, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ error: "Not found" }));
    return;
  }

  if (SHARED_SECRET && req.headers["x-worker-secret"] !== SHARED_SECRET) {
    res.writeHead(403, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ error: "Forbidden worker secret" }));
    return;
  }

  const profile = req.headers["x-build-profile"] === "release" ? "release" : "debug";
  const jobId = `job_${crypto.randomBytes(8).toString("hex")}`;
  const jobDir = path.join(WORK_ROOT, jobId);
  const zipPath = path.join(jobDir, "input.zip");
  const extractDir = path.join(jobDir, "src");
  const logs = [];

  try {
    await fs.promises.mkdir(extractDir, { recursive: true });
    const chunks = [];
    let totalBytes = 0;
    for await (const chunk of req) {
      totalBytes += chunk.length;
      if (totalBytes > 25 * 1024 * 1024) {
        throw new Error("Archive exceeds 25 MB worker limit.");
      }
      chunks.push(chunk);
    }
    await fs.promises.writeFile(zipPath, Buffer.concat(chunks));

    // Inspect zip entries against zip-slip traversal before extracting
    const { stdout: listOut } = await execFileAsync("unzip", ["-Z1", zipPath], { timeout: 15_000 });
    for (const entry of listOut.split("\n").map((s) => s.trim()).filter(Boolean)) {
      if (entry.startsWith("/") || entry.includes("..")) {
        throw new Error(`Unsafe archive entry rejected: ${entry}`);
      }
    }

    await execFileAsync("unzip", ["-q", zipPath, "-d", extractDir], { timeout: 30_000 });
    const topDirs = await fs.promises.readdir(extractDir);
    const projectDir = topDirs.length === 1 ? path.join(extractDir, topDirs[0]) : extractDir;

    await runStep("flutter", ["pub", "get"], projectDir, logs, jobDir);
    await runStep("flutter", ["analyze"], projectDir, logs, jobDir);
    await runStep("flutter", ["test"], projectDir, logs, jobDir);
    await runStep("flutter", ["build", "apk", `--${profile}`], projectDir, logs, jobDir);

    const apkRel =
      profile === "release"
        ? "build/app/outputs/flutter-apk/app-release.apk"
        : "build/app/outputs/flutter-apk/app-debug.apk";
    const apkPath = path.join(projectDir, apkRel);
    const stat = await fs.promises.stat(apkPath);
    if (!stat.isFile() || stat.size <= 0) {
      throw new Error("Compiled APK file is missing or zero-length.");
    }

    const apkBytes = await fs.promises.readFile(apkPath);
    const sha256 = crypto.createHash("sha256").update(apkBytes).digest("hex");
    logs.push(`[Validator] Verified non-empty ${profile} APK (${stat.size} bytes, SHA-256: ${sha256})`);

    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(
      JSON.stringify({
        status: "succeeded",
        exitCode: 0,
        sanitizedLogs: logs.join("\n"),
        sha256,
        apkBase64: apkBytes.toString("base64")
      })
    );
  } catch (err) {
    logs.push(`[Error] ${sanitizeOutput(err?.stderr || err?.message || String(err), jobDir)}`);
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(
      JSON.stringify({
        status: "failed",
        exitCode: 1,
        sanitizedLogs: logs.join("\n")
      })
    );
  } finally {
    await fs.promises.rm(jobDir, { recursive: true, force: true }).catch(() => {});
  }
});

server.listen(PORT, () => {
  console.log(`[isolated-build-worker] listening on :${PORT}`);
});
