# DEPLOYMENT.md — SACHET AI

> Status: NORMATIVE stub (Phase 0). Local-first; no public-cloud assumption. Security wins over convenience.

## 1. Topology (docker compose, dev)

```
              ┌──────────┐
              │ frontend │ :3000 (dev only)
              └────┬─────┘
                   │ /api/v1
              ┌────▼─────┐   ┌──────────┐  ┌────────┐
              │ backend  │──▶│ postgres │  │ redis  │ (workers)
              │  :8000   │   │ :5432    │  └────────┘
              └────┬─────┘   │+pgvector │
                   │         └──────────┘
            storage/ volume (bind-mount ./storage)
```

Services: `db (pgvector/pgvector:pg16)`, `redis`, `backend`, `frontend`, optional `qdrant`, optional `worker`. Named volumes: `pgdata, redisdata`. Healthchecks on all services.

## 2. Production Boundaries (self-hosted)

- Reverse proxy (TLS termination) → backend (`/api/v1`) + frontend static. No direct DB/Redis exposure.
- Network segmentation: `frontend-net` (proxy↔app) vs `data-net` (app↔db/redis/vector/storage). Firewall denies inter-tenant routing.
- Model servers (LLM/embedding/STT/TTS) run inside boundary or via approved gateway; external calls gated by `ModelPolicy` + classification (`SENSITIVE_MODE=true` blocks by default).

## 3. TLS & Secrets

- TLS: proxy holds certs (`TLS_CERT_PATH`, `TLS_KEY_PATH`); internal mTLS where available; HSTS enabled.
- Secrets: vault or env-file with `0600`, never in images/git. Required: `JWT_SECRET, DATABASE_URL, REDIS_URL, STORAGE_ENCRYPTION_KEY`. Rotation: JWT 90d, DB creds per policy. Secret scanning in CI.

## 4. Database / Storage / Vector

- DB: PostgreSQL 16 + `vector` extension, nightly `pg_dump` (encrypted), PITR where available. Migrations via Alembic on deploy (backup first).
- Storage: `./storage` → managed volume (or S3-compatible bucket with private ACL, versioning, object-lock for audit WORM). Quotas enforced.
- Vector: pgvector default (co-located, HNSW index); Qdrant optional sidecar (`QDRANT_URL`) with same permission payload filters. Rebuild runbook from `document_chunks`.

## 5. Model Servers

- Behind `Model Gateway` abstraction; backend never calls vendor SDKs directly. Internal stub in dev (`MODEL_GATEWAY_PROVIDER=internal-stub`).
- Prod: allowlisted endpoints only (`MODEL_GATEWAY_URL`), classification ceiling per provider (`model_providers.allowed_classifications`). External-block events audited.

## 6. Backup / Logging / Monitoring

- Backup: nightly DB + storage snapshot; retention per `docs/STORAGE_ARCHITECTURE.md`; restore drill quarterly (NOT IMPLEMENTED runbook until Phase 6).
- Logging: structured JSON to stdout → collector; `LOG_LEVEL=INFO`; never log chunk text, secrets, or audio. Audit logs separate from app logs.
- Monitoring: `/api/v1/health` probes (db/redis/vector); metrics: request rate, denial rate, ingestion lag, external-block rate; alerts on `audit.chain_broken`, denial bursts.

## 7. Deployment Steps (dev)

1. `cp .env.example .env` (fill placeholders locally, never commit).
2. `docker compose up --build` — wait for `db` healthy → migrations → backend.
3. Verify: `GET /api/v1/health` → `{"status":"ok"}`.
4. Seed dev roles (script NOT IMPLEMENTED — Phase 1).

## 8. Prod Checklist (NOT IMPLEMENTED automation)

- [ ] Secrets in vault, TLS certs valid, `DEBUG=false`, `SENSITIVE_MODE=true`.
- [ ] `ALLOW_EXTERNAL_MODELS=false`, `FEATURE_CROSS_TENANT_RETRIEVAL=false` unless approved + audited.
- [ ] Backups encrypted + restore tested; audit WORM mirror attached.
- [ ] `rg -i "api[_-]?key|secret|password"` clean; image scan green.

## 9. References

- `docker-compose.yml`, `docs/CONFIGURATION.md`, `docs/STORAGE_ARCHITECTURE.md`, `docs/AUDIT_LOGGING.md`.
