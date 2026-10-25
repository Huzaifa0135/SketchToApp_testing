# SketchToApp AI — Isolated Flutter APK Build Worker

Untrusted user-generated Flutter source code is **never** compiled inside the Android mobile client or inside the primary API process. Instead, each build runs inside an isolated, non-root, resource-capped Docker container.

## Run with Hardened Resource & Privilege Limits

```bash
docker build -t sketchtoapp-worker:latest ./build-worker

docker run --rm \
  --name sketchtoapp-worker \
  --user sketchworker \
  --cpus="2.0" \
  --memory="4g" \
  --pids-limit=256 \
  --security-opt=no-new-privileges:true \
  -e BUILD_WORKER_SHARED_SECRET="CHANGE_ME_STRONG_SECRET" \
  -p 8090:8090 \
  sketchtoapp-worker:latest
```

## Verification Pipeline per Build
1. Rejects archives > 25 MB or containing `..` / absolute paths (`unzip -Z1` zip-slip guard).
2. Runs `flutter pub get`, `flutter analyze`, `flutter test`, and `flutter build apk --debug|--release` under strict per-step timeouts and sanitized environment variables.
3. Verifies the output `.apk` exists, is non-empty, and computes its `SHA-256` digest before returning to `/backend`.
4. Deletes the temporary `/opt/sketch-worker/work/<jobId>` directory in `finally`.
