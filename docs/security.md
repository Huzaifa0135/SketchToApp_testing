# SketchToApp AI — Security Architecture

1. **Authentication Exclusivity**: Uses Google Sign-In via Jetpack `CredentialManager` (`GetSignInWithGoogleOption`) backed by Firebase Authentication. Anonymous and password-based demo logins are strictly prohibited.
2. **Zero-Trust Cloud Firestore Rules (`firestore.rules`)**:
   - Denies all paths by default.
   - Enforces `resource.data.userId == request.auth.uid` on every read/update/delete and `request.resource.data.userId == request.auth.uid` on every create.
   - Prevents privilege escalation (`planTier` locked to `"free"` on client create and immutable on client update; `aiCallsUsed` and `buildsUsed` monotonic non-decreasing).
   - Enforces `hasOnly(...)` field whitelists, bounded string/list lengths, hex color validation, and timestamp bounds (`<= request.time`).
3. **Zip-Slip & Identifier Sanitization**:
   - Both `FlutterProjectGenerator.isSafeRelativePath` and `build-worker/worker.mjs` (`unzip -Z1`) reject `..`, leading `/`, null bytes, and shell metacharacters.
4. **Sandboxed Remote Compilation**:
   - Untrusted Flutter source is never compiled inside the Android handset or primary API process; `build-worker` runs as non-root `sketchworker` with `--cpus`, `--memory`, `--pids-limit`, and `--security-opt=no-new-privileges:true`.
5. **Honest Failure Disclosure**:
   - When `BACKEND_API_URL` is unconfigured, Build Lab records an explicit diagnostic log and never fabricates build progress or fake `.apk` files.
