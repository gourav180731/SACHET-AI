# DATABASE_SCHEMA.md — SACHET AI

> Status: NORMATIVE stub (Phase 0). PostgreSQL 16 + pgvector default; Qdrant optional.
> All retrieval MUST join permission tables BEFORE vector similarity (RBAC pre-retrieval).

## 1. Conventions

- PK: `uuid` (uuid7 preferred). Timestamps: `timestamptz` (`created_at`, `updated_at`).
- Classification enum: `PUBLIC < INTERNAL < CONFIDENTIAL < SECRET < TOP_SECRET`.
- `security_context` per request: `{user_id, roles, clearance, department, project_scopes}`.
- Alembic migrations only; no ad-hoc DDL. Soft-delete (`deleted_at`) except audit logs (append-only).

## 2. ER Overview

```
users ──< user_roles >── roles ──< role_permissions >── permissions
users ──< document_permissions (user grant)
roles ──< document_permissions (role grant)
documents ──< document_versions ──< document_chunks
documents ──< ingestion_jobs
documents ──< document_permissions
users ──< conversations ──< messages
users ──< generated_files
users ──< audit_logs
model_providers + language_capabilities + tool_registry (config tables)
```

## 3. Core Tables

### 3.1 users / roles / permissions (RBAC)
- `users(id, email UNIQUE, display_name, department, clearance, is_active, mfa_enabled, created_at)`.
- `roles(id, name UNIQUE, description)` — e.g. `admin, auditor, analyst, viewer, ingestion-operator`.
- `permissions(id, key UNIQUE, description)` — e.g. `doc:read, doc:write, export:high, audit:read, admin:config`.
- `user_roles(user_id FK, role_id FK, scope JSONB)` — scope: `{department, project_scopes}`.
- `role_permissions(role_id FK, permission_id FK)`.

### 3.2 documents / document_versions
- `documents(id, tenant_id, title, classification, owner_id FK, department, project_scope, storage_uri, checksum_sha256, current_version_id, retention_until, deleted_at, created_at)`.
- `document_versions(id, document_id FK, version INT, storage_uri, checksum_sha256, page_count, parser, language, created_by, created_at)`.
- Every grounded citation uses `(document_id, version, page, section)`.

### 3.3 document_permissions (document-level ACL)
- `document_permissions(id, document_id FK, principal_type ENUM[user,role,department], principal_id TEXT, can_read BOOL, can_write BOOL, granted_by, expires_at)`.
- Retrieval filter: caller must match ≥1 unexpired grant AND `clearance >= classification` AND department/scope match.
- No grant → invisible (not just masked) to caller.

### 3.4 document_chunks (RAG + vectors)
- `document_chunks(id, document_id FK, version INT, chunk_index INT, page INT, section TEXT, text TEXT, embedding vector(768), token_count INT, language TEXT, created_at)`.
- `embedding` dimension configurable (default 768); migration required to change.
- Text stored encrypted at rest (volume/TDE); never logged wholesale.

### 3.5 ingestion_jobs (async pipeline)
- `ingestion_jobs(id, document_id FK, version INT, state ENUM, progress_pct, error_code, error_detail, attempts, worker_id, created_by, updated_at)`.
- States: `QUEUED → VALIDATING → PARSING → OCR → CHUNKING → EMBEDDING → INDEXING → READY | FAILED`.
- `FAILED` retains error + attempt history; never silently flips to `READY`.

### 3.6 conversations / messages (memory separation)
- `conversations(id, user_id FK, title, retention_until, created_at)`.
- `messages(id, conversation_id FK, role ENUM[user,assistant,tool], content TEXT, citations JSONB, model_id TEXT, created_at)`.
- `citations`: `[{document_id, version, page, section, chunk_id}]`. No auto-copy of doc text into long-term memory.

### 3.7 audit_logs (append-only)
- `audit_logs(event_id PK, occurred_at, actor_id FK, actor_roles TEXT[], action TEXT, doc_ids uuid[], classification TEXT, pages INT[], model_id TEXT, tool_name TEXT, result TEXT, request_id, prev_hash TEXT, event_hash TEXT)`.
- INSERT-only grant; no UPDATE/DELETE for app role. See `docs/AUDIT_LOGGING.md`.

### 3.8 generated_files / config tables
- `generated_files(id, user_id FK, kind ENUM[report,pptx,export], storage_uri, source_citations JSONB, classification, expires_at, created_at)`.
- `model_providers(id, name UNIQUE, kind ENUM[llm,embedding,stt,tts], endpoint, allow_external BOOL, allowed_classifications TEXT[])` — gateway registry.
- `language_capabilities(code PK, stt BOOL, tts BOOL, rag BOOL, notes TEXT)`.
- `tool_registry(name PK, risk_level ENUM[LOW,MEDIUM,HIGH,CRITICAL], requires_confirmation BOOL, allowed_roles TEXT[])`.

## 4. Indexes (incl. vector HNSW)

```sql
CREATE EXTENSION IF NOT EXISTS vector;
CREATE INDEX ON documents (tenant_id, classification, department);
CREATE INDEX ON document_permissions (document_id, principal_type, principal_id);
CREATE INDEX ON document_versions (document_id, version);
CREATE INDEX ON document_chunks (document_id, version);
CREATE INDEX ON ingestion_jobs (state, updated_at);
CREATE INDEX ON messages (conversation_id, created_at);
CREATE INDEX ON audit_logs (occurred_at, action, actor_id);
-- Vector similarity (permission-filtered CTE first, then order by distance):
CREATE INDEX ON document_chunks USING hnsw (embedding vector_cosine_ops);
```

Qdrant (optional): mirror `(document_id, version, chunk_id)` + payload `{classification, department, project_scope}`; same pre-filter required.

## 5. Migration & Seed Strategy

- Alembic chain: `0001_rbac → 0002_documents → 0003_chunks_vector → 0004_jobs_conv_audit`.
- Seed (dev only): roles/permissions, `tool_registry`, `language_capabilities`, demo users — never production secrets.
- Vector dimension change = new migration + re-embed job, not in-place alter.

## 6. Acceptance (Phase 0)

- [ ] All 14 entities defined; permission-filter-before-vector rule explicit.
- [ ] HNSW index + async states match `docs/RAG_ARCHITECTURE.md` and pipeline docs.
- [ ] No real secrets or connection strings in this file.
