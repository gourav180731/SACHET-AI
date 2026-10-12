# STORAGE_ARCHITECTURE.md — SACHET AI

> Status: NORMATIVE stub (Phase 0). Four separated stores; no single store holds everything.

## 1. Store Separation (Non-Negotiable)

| Store | Holds | Technology (default) |
|---|---|---|
| Document Storage | Original bytes + generated files, versioned | Local filesystem/S3-compatible volume (`STORAGE_BACKEND=local|s3`), NOT the DB |
| Metadata DB | Users, docs, versions, permissions, jobs, conversations | PostgreSQL 16 |
| Vector Index | Embeddings + `(doc_id, version, page, chunk_id)` + filter payload | pgvector (in-Postgres, default); Qdrant optional mirror |
| Audit Logs | Hash-chained events, no content | Postgres `audit` schema, INSERT-only; WORM mirror in prod |

Rationale: least privilege per store, independent scaling/retention/backup, blast-radius containment. Never `User → LLM → unrestricted store`.

## 2. Document Storage Layout

```
storage/
  originals/{tenant_id}/{document_id}/v{n}/{sha256}.{ext}
  generated/{tenant_id}/{generated_file_id}.{pdf|pptx}
  tmp/uploads/{job_id}/...
```

- Content-addressed filenames (`sha256`); DB holds `storage_uri + checksum_sha256`.
- `tmp/` purged hourly; quarantine dir for `VALIDATING`-rejected files.
- Local-first default (`./storage` bind-mount in compose); S3-compatible via gateway interface (`StorageProvider`) — app talks to interface, not SDK directly.

## 3. Encryption

- In transit: TLS everywhere (reverse proxy / internal TLS; `TLS_ENABLED=true` in prod).
- At rest: volume-level encryption for Document Storage; Postgres TDE/volume encryption for Metadata + Vectors + Audit.
- Key management: env/secrets manager only (`STORAGE_ENCRYPTION_KEY`, `DB_ENCRYPTION_KEY` via vault); never in git. Key rotation runbook — NOT IMPLEMENTED (Phase 6).
- No application-level per-document encryption in Phase 0 (documented limitation).

## 4. Versioning

- Immutable versions: new upload → `document_versions(version+1)` + new storage object; `documents.current_version_id` pointer moves only after ingestion reaches `READY`.
- Citations pin `(document_id, version)`; retrieval defaults to `current_version` unless explicit version requested and authorized.
- Version restore = new version pointing at prior bytes (audit `doc.version_restore`).

## 5. Retention & Lifecycle

- Per-document `retention_until` from classification policy (see `docs/CONFIGURATION.md`): PUBLIC 1y / INTERNAL 1y / CONFIDENTIAL 3y / SECRET+ 7y defaults, tenant-overridable.
- Conversations/messages + `generated_files.expires_at` purged by scheduled job; purge emits audit event with counts only.
- Legal hold flag suspends purge for matching `document_id`s.

## 6. Secure Deletion

- Soft-delete first (`deleted_at`); hard delete = remove storage bytes + vector rows + mark metadata purged, keep audit events (doc_ids retained, content never stored there anyway).
- Overwrite/secure-erase for local volumes where FS supports it; S3 versioned-delete + object-lock expiry otherwise.
- Deletion is CRITICAL tool: explicit confirmation + `doc.delete` audit + admin approval for SECRET+.

## 7. Backup & Disaster Recovery

- Nightly: Postgres `pg_dump` + storage snapshot; vector index rebuilt from `document_chunks` if needed (pgvector) or re-synced (Qdrant).
- Backups encrypted, same classification as source; restore tested quarterly (runbook NOT IMPLEMENTED until Phase 6).
- RPO/RTO targets defined per deployment (local dev: best-effort; prod: RPO 24h, RTO 4h defaults).

## 8. Quotas & Isolation

- Per-tenant quotas (`STORAGE_QUOTA_GB`); cross-tenant retrieval OFF by default (`FEATURE_CROSS_TENANT_RETRIEVAL=false`).
- Storage gateway enforces `tenant_id` prefix on every read/write; path traversal rejected.

## 9. Acceptance (Phase 0)

- [ ] Four-store diagram matches `docs/ARCHITECTURE.md` + compose volumes.
- [ ] No design stores full text in audit logs or embeddings metadata beyond `chunk_id` refs.
- [ ] Secure defaults (no public bucket, cross-tenant OFF) explicit.

## 10. References

- `docs/DATABASE_SCHEMA.md`, `docs/CONFIGURATION.md`, `docker-compose.yml`.
