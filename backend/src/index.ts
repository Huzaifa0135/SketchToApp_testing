import express, { Request, Response, NextFunction } from "express";
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import multer from "multer";
import { initializeApp, applicationDefault } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { z } from "zod";

initializeApp({ credential: applicationDefault() });

const app = express();
app.use(express.json({ limit: "4mb" }));

const uploadDir = path.resolve(process.cwd(), "tmp_uploads");
fs.mkdirSync(uploadDir, { recursive: true });
const artifactsDir = path.resolve(process.cwd(), "verified_artifacts");
fs.mkdirSync(artifactsDir, { recursive: true });

const upload = multer({
  dest: uploadDir,
  limits: { fileSize: 25 * 1024 * 1024 } // 25 MB archive limit
});

// Simple per-UID sliding-window rate limiter
const rateBuckets = new Map<string, number[]>();
export function checkRateLimit(uid: string, limitPerMinute = 30): boolean {
  const now = Date.now();
  const recent = (rateBuckets.get(uid) ?? []).filter((t) => now - t < 60_000);
  if (recent.length >= limitPerMinute) {
    rateBuckets.set(uid, recent);
    return false;
  }
  recent.push(now);
  rateBuckets.set(uid, recent);
  return true;
}

export function isSafeRelativeArtifactPath(relPath: string): boolean {
  if (!relPath || relPath.startsWith("/") || relPath.startsWith("\\")) return false;
  if (relPath.includes("..") || relPath.includes("\0")) return false;
  return /^[a-zA-Z0-9_./-]+$/.test(relPath);
}

interface AuthedRequest extends Request {
  uid?: string;
}

async function requireFirebaseAuth(req: AuthedRequest, res: Response, next: NextFunction): Promise<void> {
  const header = req.headers.authorization ?? "";
  if (!header.startsWith("Bearer ")) {
    res.status(401).json({ error: "Missing Bearer Firebase ID token." });
    return;
  }
  const token = header.slice("Bearer ".length).trim();
  try {
    const decoded = await getAuth().verifyIdToken(token);
    if (!checkRateLimit(decoded.uid)) {
      res.status(429).json({ error: "Rate limit exceeded. Try again in 60 seconds." });
      return;
    }
    req.uid = decoded.uid;
    next();
  } catch {
    res.status(401).json({ error: "Invalid or expired Firebase ID token." });
  }
}

interface BuildState {
  buildId: string;
  ownerUid: string;
  projectId: string;
  buildProfile: "debug" | "release";
  status: "queued" | "preparing" | "building" | "validating" | "succeeded" | "failed";
  exitCode: number;
  sanitizedLogs: string;
  artifactPath: string;
  artifactSha256: string;
  artifactSizeBytes: number;
}

const buildStore = new Map<string, BuildState>();

const BuildRequestSchema = z.object({
  projectId: z.string().min(1).max(128).regex(/^[a-zA-Z0-9_-]+$/),
  buildProfile: z.enum(["debug", "release"]).default("debug")
});

app.get("/healthz", (_req, res) => {
  res.json({ status: "ok", service: "sketchtoapp-backend" });
});

app.post(
  "/v1/builds",
  requireFirebaseAuth,
  upload.single("sourceZip"),
  async (req: AuthedRequest, res: Response) => {
    const parsed = BuildRequestSchema.safeParse(req.body);
    if (!parsed.success || !req.file) {
      if (req.file) fs.unlink(req.file.path, () => {});
      res.status(400).json({ error: "Valid projectId, buildProfile, and sourceZip archive required." });
      return;
    }

    const buildId = `bld_${crypto.randomBytes(8).toString("hex")}`;
    const state: BuildState = {
      buildId,
      ownerUid: req.uid!,
      projectId: parsed.data.projectId,
      buildProfile: parsed.data.buildProfile,
      status: "queued",
      exitCode: -1,
      sanitizedLogs: "[Backend] Queued source snapshot for isolated Docker Flutter worker.\n",
      artifactPath: "",
      artifactSha256: "",
      artifactSizeBytes: 0
    };
    buildStore.set(buildId, state);

    const workerUrl = process.env.BUILD_WORKER_URL ?? "http://127.0.0.1:8090";
    const workerSecret = process.env.BUILD_WORKER_SHARED_SECRET ?? "";

    // Dispatch asynchronously to isolated build-worker
    void (async () => {
      try {
        state.status = "building";
        const zipBytes = await fs.promises.readFile(req.file!.path);
        const resp = await fetch(`${workerUrl}/compile`, {
          method: "POST",
          headers: {
            "Content-Type": "application/octet-stream",
            "X-Worker-Secret": workerSecret,
            "X-Build-Id": buildId,
            "X-Build-Profile": state.buildProfile
          },
          body: zipBytes
        });
        const payload = (await resp.json()) as {
          status: "succeeded" | "failed";
          exitCode: number;
          sanitizedLogs: string;
          apkBase64?: string;
          sha256?: string;
        };

        state.exitCode = payload.exitCode;
        state.sanitizedLogs = payload.sanitizedLogs;

        if (resp.ok && payload.status === "succeeded" && payload.apkBase64 && payload.sha256) {
          const apkBuffer = Buffer.from(payload.apkBase64, "base64");
          const actualSha = crypto.createHash("sha256").update(apkBuffer).digest("hex");
          if (apkBuffer.length > 0 && actualSha === payload.sha256) {
            const outFile = path.join(artifactsDir, `${buildId}-${state.buildProfile}.apk`);
            await fs.promises.writeFile(outFile, apkBuffer);
            state.artifactPath = outFile;
            state.artifactSha256 = actualSha;
            state.artifactSizeBytes = apkBuffer.length;
            state.status = "succeeded";
          } else {
            state.status = "failed";
            state.sanitizedLogs += "\n[Backend] Worker APK failed SHA-256 or non-empty verification.";
          }
        } else {
          state.status = "failed";
        }
      } catch (err) {
        state.status = "failed";
        state.exitCode = 2;
        state.sanitizedLogs += `\n[Backend] Isolated worker unreachable: ${(err as Error).message}`;
      } finally {
        fs.unlink(req.file!.path, () => {});
      }
    })();

    res.status(202).json({
      buildId: state.buildId,
      status: state.status,
      exitCode: state.exitCode,
      sanitizedLogs: state.sanitizedLogs,
      artifactDownloadUrl: "",
      artifactSha256: "",
      artifactSizeBytes: 0
    });
  }
);

app.get("/v1/builds/:buildId", requireFirebaseAuth, (req: AuthedRequest, res: Response) => {
  const state = buildStore.get(req.params.buildId);
  if (!state || state.ownerUid !== req.uid) {
    res.status(404).json({ error: "Build job not found." });
    return;
  }
  const baseUrl = `${req.protocol}://${req.get("host")}`;
  res.json({
    buildId: state.buildId,
    status: state.status,
    exitCode: state.exitCode,
    sanitizedLogs: state.sanitizedLogs,
    artifactDownloadUrl:
      state.status === "succeeded" ? `${baseUrl}/v1/artifacts/${state.buildId}/download` : "",
    artifactSha256: state.artifactSha256,
    artifactSizeBytes: state.artifactSizeBytes
  });
});

app.get("/v1/artifacts/:buildId/download", requireFirebaseAuth, (req: AuthedRequest, res: Response) => {
  const state = buildStore.get(req.params.buildId);
  if (!state || state.ownerUid !== req.uid || state.status !== "succeeded" || !state.artifactPath) {
    res.status(404).json({ error: "Verified artifact not available." });
    return;
  }
  res.setHeader("Content-Type", "application/vnd.android.package-archive");
  res.setHeader("X-Checksum-Sha256", state.artifactSha256);
  res.sendFile(state.artifactPath);
});

const port = Number(process.env.PORT ?? 8080);
if (process.env.NODE_ENV !== "test") {
  app.listen(port, () => {
    console.log(`[sketchtoapp-backend] listening on :${port}`);
  });
}
