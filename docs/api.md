# SketchToApp AI — API Specification

## Authentication
All `/v1/*` endpoints require `Authorization: Bearer <Firebase_ID_Token>`. Requests without a valid token return `401 Unauthorized`; requests exceeding 30 RPM per UID return `429 Too Many Requests`.

## Endpoints (`backend/src/index.ts`)
- **`GET /healthz`** — Health probe (`200 OK`).
- **`POST /v1/builds`** (`multipart/form-data`)
  - Fields: `projectId` (`^[a-zA-Z0-9_-]+$`), `buildProfile` (`debug` | `release`), `sourceZip` (verified Flutter `.zip`, max 25 MB).
  - Response (`202 Accepted`): `{ buildId, status, exitCode, sanitizedLogs, artifactDownloadUrl, artifactSha256, artifactSizeBytes }`.
- **`GET /v1/builds/:buildId`**
  - Enforces `state.ownerUid === req.uid`. Returns live build state, sanitized logs, and protected download URL once `status === "succeeded"`.
- **`GET /v1/artifacts/:buildId/download`**
  - Enforces ownership and `status === "succeeded"`. Streams `application/vnd.android.package-archive` with `X-Checksum-Sha256` header.
