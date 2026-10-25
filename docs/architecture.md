# SketchToApp AI — System Architecture

## 1. End-to-End Overview
SketchToApp AI transforms hand-drawn mobile wireframes and Figma frames into real, multi-screen Material 3 Flutter Android applications through a versioned **Canonical UI Schema (`CURRENT_SCHEMA_VERSION = 1`)**.

### Architectural Layers
1. **Native Android Workstation (`app/`)**:
   - **UI Layer**: Kotlin + Jetpack Compose + Material 3 dark workstation (`SketchToAppTheme`), 23 type-safe `@Serializable` routes (`WorkstationDestination`).
   - **Domain & Schema Layer**: `CanonicalSchema.kt` (`CanonicalApplicationDocument`, `CanonicalScreen`, `CanonicalComponent`, `CanonicalThemeTokens`) and `SchemaValidator.kt` (duplicate IDs, route cycles/broken targets, safe identifier sanitization, color token validation).
   - **AI & Import Services**:
     - `GeminiAgentsService.kt`: Multimodal Sketch Design Analysis (`gemini-3.1-pro-preview`), UI Mapping, Flutter Code Quality Review, Build Orchestration, and Architecture Assistant (`gemini-3.5-flash`).
     - `FigmaImportService.kt`: Figma REST API v1 frame discovery and deterministic node-to-canonical mapping.
   - **Deterministic Code Generator & Exporter**:
     - `FlutterProjectGenerator.kt`: Generates `pubspec.yaml`, `analysis_options.yaml`, `lib/main.dart`, `lib/theme/app_theme.dart`, `lib/screens/*_screen.dart`, `lib/widgets/sketch_placeholder_box.dart`, `test/widget_test.dart`, and `README.md`.
     - `ZipAndArtifactExporter.kt`: Packages verified `.zip` archives with Zip-Slip path guards, SHA-256 checksums, `ActivityResultContracts.CreateDocument`, and `FileProvider` sharing.
   - **Data Layer (`SketchToAppRepository.kt`)**: Constructor-injected named Cloud Firestore database (`R.string.firestore_database_id`) and Google Sign-In via Jetpack `CredentialManager`.
2. **Backend Orchestrator (`backend/`)**:
   - Express + Firebase Admin token verification, per-UID sliding-window rate limiting, Zod request validation, isolated worker dispatch, and protected SHA-256 verified artifact streaming.
3. **Isolated APK Build Worker (`build-worker/`)**:
   - Non-root Docker container (`flutter:3.27.0`) running `flutter pub get`, `flutter analyze`, `flutter test`, and `flutter build apk` with strict step timeouts and temporary workspace cleanup.
