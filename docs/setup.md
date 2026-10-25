# SketchToApp AI — Setup Guide

## 1. AI Studio Secrets Configuration
Open the **Secrets panel in AI Studio** and configure:
- `GEMINI_API_KEY`: Required for Gemini 3.1 Pro multimodal sketch analysis and Gemini 3.5 Flash architecture assistant.
- `FIGMA_ACCESS_TOKEN`: Optional Personal Access Token for importing live frames from Figma REST API v1.
- `BACKEND_API_URL`: Optional HTTPS URL of your deployed `/backend` orchestrator connected to `/build-worker` for remote APK builds.

*(Never place API keys in `local.properties` or hardcode them in source files.)*

## 2. Running the Isolated Build Worker & Backend Locally
```bash
# 1. Build and start the isolated Flutter 3.27 Docker worker
docker build -t sketchtoapp-worker:latest ./build-worker
docker run --rm -p 8090:8090 \
  --cpus="2.0" --memory="4g" --pids-limit=256 \
  --security-opt=no-new-privileges:true \
  -e BUILD_WORKER_SHARED_SECRET="local-dev-secret" \
  sketchtoapp-worker:latest

# 2. Start the backend API
cd backend
cp .env.example .env
npm install && npm run build && npm start
```
