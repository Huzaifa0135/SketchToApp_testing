# SketchToApp AI — Testing & Verification Matrix

## 1. Firestore Security Rules (Tier 1 JS & Tier 2 Kotlin SDK)
- **Tier 1 (`firestore.test.js`)**: `node --test firestore.test.js` — verifies unauthenticated denial, cross-user BOLA isolation, planTier privilege-escalation rejection, verified artifact SHA-256 enforcement, and immutable usage logs against the Firebase Firestore Emulator (`127.0.0.1:8085`).
- **Tier 2 (`SketchToAppRepositoryRuleTest.kt`)**: `gradle :app:testDebugUnitTest --tests '*Rule*'` — exercises the real `SketchToAppRepository` Kotlin serialization pipeline against live Firestore & Auth emulators.

## 2. Deterministic Generator, Schema Validator & ZIP Exporter (`ExampleUnitTest.kt`)
- `gradle :app:testDebugUnitTest --tests 'com.example.ExampleUnitTest'` — verifies multi-screen Dart code generation, form validation emission, Zip-Slip path rejection, Figma URL key extraction, and non-empty `.zip` packaging with SHA-256 verification.

## 3. Android Compose UI & Auth Gate (`ExampleRobolectricTest.kt`)
- `gradle :app:testDebugUnitTest --tests 'com.example.ExampleRobolectricTest'` — verifies Compose rendering and test tags on local JVM via Robolectric without requiring a hardware emulator.

## 4. Backend Security Unit Tests (`backend/test/backend.test.js`)
- `node --test backend/test/backend.test.js` — verifies archive path traversal rejection, build log secret redaction, and SHA-256 tamper detection.
