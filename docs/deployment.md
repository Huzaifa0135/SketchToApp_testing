# SketchToApp AI — Deployment Guide

## 1. Firestore Security Rules & Indexes
Firestore security rules (`firestore.rules`) target the named database `ai-studio-android-sketchto-57cfd153-e1d9-48e7-8080-71ce4535fc73` in project `seatbookingapp-495511`.

## 2. Backend & Build Worker Deployment
- Deploy `backend/` to Cloud Run (or any HTTPS container host) with `GOOGLE_APPLICATION_CREDENTIALS` / workload identity for Firebase ID token verification.
- Deploy `build-worker/` to a dedicated isolated VM or Cloud Run Job / GKE Autopilot pool with `--security-opt=no-new-privileges:true`, CPU/RAM quotas, and `BUILD_WORKER_SHARED_SECRET`.
- Point `BACKEND_API_URL` in the AI Studio Secrets panel to the HTTPS URL of `backend/`.

## 3. Android APK / AAB Release
Export the signed Android project from AI Studio Settings to generate installable APK or Google Play AAB bundles (`applicationId = "com.aistudio.sketchtoapp.kxmzpq"`).
