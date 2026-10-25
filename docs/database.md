# SketchToApp AI — Cloud Firestore Database Schema

Named Database ID: `ai-studio-android-sketchto-57cfd153-e1d9-48e7-8080-71ce4535fc73`

## Collections (10 Zero-Trust Collections)
1. **`users/{userId}`**: Developer profile, plan tier (`free`/`pro`), `onboardingCompleted`, and quota counters (`aiCallsUsed`, `buildsUsed`).
2. **`projects/{projectId}`**: Project metadata (`name`, `packageName`, `sourceType`, `stage`, `screenCount`, `schemaVersion`), owned via immutable `userId`.
3. **`designs/{designId}`**: Imported sketch/Figma analysis records, warning counts, and source references.
4. **`application_documents/{projectId}`**: Canonical application root (`appName`, `initialScreenId`, theme hex tokens, `fontFamily`, `darkMode`, `borderRadiusDp`, `screenOrder`).
5. **`screens/{screenId}`**: Canonical screens (`projectId`, `name`, `route`, `isInitial`, `orderIndex`, serialized `components` list).
6. **`assets/{assetId}`**: Validated sketch/image metadata (`mimeType`, `sizeBytes`, `checksumSha256`, `storageRef`).
7. **`generation_jobs/{jobId}`**: Flutter source generation snapshots, file counts, diagnostics summaries, and `.zip` SHA-256 checksums.
8. **`build_jobs/{buildId}`**: Isolated APK build states (`queued`, `preparing`, `building`, `validating`, `succeeded`, `failed`), `workerMode`, `exitCode`, and `sanitizedLogs`.
9. **`generated_apps/{appId}`**: Verified APK artifact records (`sizeBytes > 0`, 64-char hex `checksumSha256`, `verified`, `downloadUrl`).
10. **`usage_logs/{logId}`**: Append-only immutable telemetry records (`sketch_analysis`, `figma_import`, `code_generation`, `apk_build`).
