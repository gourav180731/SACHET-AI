# CONFIGURATION.md — SACHET AI

> Status: NORMATIVE (Phases 1–2 implemented). Single source: `backend/src/main/java/ai/sachet/config/` (`SachetProperties` + fail-fast `SachetPropertiesValidator`, `SACHET_*` env). No secrets in code/git.

## 1. Principles

- Centralized config only (`ai.sachet.config`); modules never read env directly.
- Typed + validated at startup; fail-fast on missing required values.
- Secure defaults: external sharing OFF, cross-tenant OFF, verbose sensitive logging OFF.
- `.env.example` holds placeholders only; real `.env` is git-ignored.

## 2. Config Table

| Area | Key | Default (secure) | Notes |
|---|---|---|---|
| App | `APP_ENV` / `APP_VERSION` / `LOG_LEVEL` | `dev` / `0.1.0` / `INFO` | `DEBUG` never in prod |
| API | `API_V1_PREFIX` / `CORS_ORIGINS` | `/api/v1` / `http://localhost:3000` | Explicit origins only |
| DB | `DATABASE_URL` | `postgresql+psycopg://sachet:sachet@db:5432/sachet` (dev only) | Prod via secrets manager |
| Queue | `REDIS_URL` / `CELERY_BROKER_URL` | `redis://redis:6379/0` | Ingestion workers |
| Auth | `JWT_SECRET` / `JWT_EXPIRE_MIN` / `MFA_REQUIRED` | `CHANGE-ME` (fail-fast) / `30` / `true` for admin/audit | Rotate via vault |
| Storage | `STORAGE_BACKEND` / `STORAGE_PATH` / `STORAGE_QUOTA_GB` | `local` / `./storage` / `10` | `s3` needs endpoint/bucket keys |
| Vector | `VECTOR_STORE` / `EMBEDDING_DIM` / `QDRANT_URL` | `pgvector` / `768` / `` (optional) | Qdrant only if explicitly enabled |
| LLM | `MODEL_GATEWAY_PROVIDER` / `MODEL_GATEWAY_URL` | `internal-stub` / `` | External needs allowlist |
| Embed/STT/TTS | `EMBEDDING_PROVIDER` / `STT_PROVIDER` / `TTS_PROVIDER` | `internal-stub` | External gated by `ModelPolicy` |
| Security | `SENSITIVE_MODE` / `ALLOW_EXTERNAL_MODELS` | `true` / `false` | External blocked when sensitive |
| Features | `FEATURE_WEB_SEARCH` / `FEATURE_CROSS_TENANT_RETRIEVAL` / `FEATURE_VERBOSE_LOGGING` | `false` / `false` / `false` | Opt-in only |
| Retention | `RETENTION_DAYS_PUBLIC/INTERNAL/CONFIDENTIAL/SECRET` | `365/365/1095/2555` | Tenant-overridable in DB |
| Tools | `TOOL_CONFIRM_HIGH` / `TOOL_CONFIRM_CRITICAL` | `true` / `true` | Cannot be disabled silently |

## 3. `backend/app/core/config.py` Contract

- `class Settings(BaseSettings)` with `model_config = SettingsConfigDict(env_file=".env", extra="ignore")`.
- Validators: `JWT_SECRET != "CHANGE-ME"` in prod; `VECTOR_STORE in {pgvector, qdrant}`; `EMBEDDING_DIM > 0`.
- Exposes `get_settings()` (cached) + `is_external_allowed(classification)` helper — NOT IMPLEMENTED beyond stub (Phase 1).
- Never logs secret values; `/security/policy` endpoint exposes booleans only.

## 4. `.env.example` Keys (placeholders only)

`DATABASE_URL, REDIS_URL, JWT_SECRET=CHANGE-ME-PLACEHOLDER, STORAGE_BACKEND, STORAGE_PATH, MODEL_GATEWAY_PROVIDER, MODEL_GATEWAY_URL, EMBEDDING_PROVIDER, VECTOR_STORE=pgvector, QDRANT_URL=, STT_PROVIDER, TTS_PROVIDER, SENSITIVE_MODE=true, ALLOW_EXTERNAL_MODELS=false, FEATURE_WEB_SEARCH=false, FEATURE_CROSS_TENANT_RETRIEVAL=false`. No real secrets.

## 5. Secure Defaults Table (Behavioral)

| Default | Value | Override Requires |
|---|---|---|
| External model calls | DENY in sensitive mode | `ModelPolicy` allow + classification ≤ allowed + explicit user opt-in + audit |
| Cross-tenant retrieval | OFF | `admin:config` + audit |
| Export/share | Approval + confirmation | `export:high` role |
| Error responses | Generic message + `request_id` | Never stack traces to client |
| Logging | No chunk/prompt content | `audit:view_sensitive` for hashes only |

## 6. Environment Matrix

- `dev` (compose): local pgvector + redis + stub gateways; `SENSITIVE_MODE=true` still enforced.
- `prod` (self-hosted): managed secrets, TLS, WORM audit mirror, real model servers behind gateway.
- No public-cloud assumption; all backends behind provider interfaces (LLM/Embedding/VectorDB/STT/TTS/OCR/Parser/Storage/Translation).

## 7. Acceptance (Phase 0)

- [ ] `config.py` stub loads env, enforces secure defaults, marks unimplemented with `NOT IMPLEMENTED`.
- [ ] `rg -i "api[_-]?key|secret|password"` clean except placeholders in `.env.example`.
- [ ] This table matches `docker-compose.yml` service names.

## 8. References

- `backend/app/core/config.py`, `.env.example`, `docs/SECURITY_ARCHITECTURE.md`, `docs/LLM_MODEL_POLICY.md`.
