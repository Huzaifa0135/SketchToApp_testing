# SketchToApp AI — Firestore Zero-Trust Security Specification (`security_spec.md`)

## 1. Data Invariants & Relational Ownership

1. **Global Deny-By-Default**: Any path not explicitly matched is rejected (`allow read, write: if false;`).
2. **Strict Multi-Tenant Ownership**: Every document across all 10 collections belongs to a single authenticated Google user (`userId == request.auth.uid`). Cross-user reads, lists, updates, and deletes are unconditionally rejected.
3. **PII Isolation on `/users/{userId}`**: User profiles contain `email`. `allow list` is disabled (`false`) on `/users`, and `allow get` requires `request.auth.uid == userId`. Self-assigned paid plan privilege escalation (`planTier != 'free'` on create or changing `planTier` on update) is blocked.
4. **Parent Gate Relational Integrity**: Creating any child document (`designs`, `application_documents`, `screens`, `assets`, `generation_jobs`, `build_jobs`, `generated_apps`) requires the referenced `/projects/{projectId}` document to exist AND be owned by `request.auth.uid`.
5. **No JSON Blobs / Bounded Collections**: Screens and component hierarchies are stored as native Firestore Maps and bounded Lists (`components.size() <= 100`, `screenOrder.size() <= 30`, `warnings.size() <= 50`).
6. **Temporal Integrity**: All `createdAt` and `updatedAt` fields must be server timestamps (`is timestamp && <= request.time`), and `createdAt`, `userId`, and `projectId` are immutable on updates.
7. **Terminal State Locking**: Once a `generation_jobs` or `build_jobs` document enters a terminal status (`succeeded` or `failed`), all subsequent client updates are rejected.
8. **Append-Only Audit Trail**: `/usage_logs/{logId}` allows authenticated owner creation and `get`/`list` with `userId` filter, while `update` and `delete` are strictly `false`.

## 2. The "Dirty Dozen" Adversarial Payloads

1. **Unauthenticated Enumeration**: Unauthenticated caller queries `/projects` -> Rejected (`request.auth != null`).
2. **Cross-User Project Theft**: `alice_123` attempts `get` on `bob_456`'s project -> Rejected (`existing().userId == request.auth.uid`).
3. **Unfiltered List Scraping**: `alice_123` executes `db.collection("projects").get()` without `.where("userId", "==", "alice_123")` -> Rejected (`resource.data.userId == request.auth.uid`).
4. **PII Directory Scraping**: `alice_123` queries `/users` collection or reads `/users/bob_456` -> Rejected (`allow list: if false` and `request.auth.uid == userId`).
5. **Privilege Escalation (Self-Assigned Pro Tier)**: `alice_123` creates `/users/alice_123` with `"planTier": "enterprise"` -> Rejected (`incoming().planTier == 'free'`).
6. **Shadow Field Injection**: `alice_123` updates `/projects/proj_1` with extra ghost key `"isAdminVerified": true` -> Rejected (`affectedKeys().hasOnly(...)` & `keys().hasOnly(...)`).
7. **Orphaned Child Write (Parent Gate Bypass)**: `alice_123` creates `/screens/scr_1` pointing to nonexistent `projectId: "ghost_proj"` or `bob_456`'s project -> Rejected (`exists(...) && get(...).data.userId == request.auth.uid`).
8. **Immutable Ownership Transfer**: `alice_123` updates `/projects/proj_1` changing `userId` to `bob_456` -> Rejected (`incoming().userId == existing().userId`).
9. **Terminal Build State Tampering**: `alice_123` updates a `failed` build job in `/build_jobs/job_1` to `succeeded` -> Rejected (`!(existing().status in ['succeeded', 'failed'])`).
10. **Audit Log Deletion/Tampering**: `alice_123` attempts to update or delete `/usage_logs/log_1` -> Rejected (`allow update, delete: if false`).
11. **Denial-of-Wallet Oversized String / ID Poisoning**: Caller injects a 500-char poisoned ID with slashes/spaces or a 20KB project name -> Rejected (`isValidId` regex/length guard & `.size() <= 80`).
12. **Value Poisoning on Update**: Caller updates allowlisted field `screenCount` with a string `"many"` instead of bounded `int` -> Rejected (`isValidProject(incoming())` wraps entire update rule).

## 3. Red Team Audit Summary

- **Shadow Update Test**: PASS — Every `create` enforces `keys().hasAll(...)` + `keys().hasOnly(...)`, and every `update` enforces `affectedKeys().hasOnly(...)` + `isValid[Entity](incoming())`.
- **PII Blanket Test**: PASS — `/users/{userId}` denies `list` completely and restricts `get` strictly to `request.auth.uid == userId`.
- **Query Trust Test**: PASS — Every `allow list` checks `resource.data.userId == request.auth.uid` with zero `get()` calls inside `list`.
- **Value & ID Poisoning Test**: PASS — `isValidId()` guards every single-document operation (`get`, `create`, `update`, `delete`), and `isValid[Entity](incoming())` wraps every `create` and `update`.
