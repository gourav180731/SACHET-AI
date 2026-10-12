# SACHET AI — backend (Java only, server side)

> Status: Phase 4 RAG implemented on Spring Boot 3 / Java 22.
> Earlier Python prototype was replaced per decision (Java-only server code).

## Purpose

Security-first service (`/api/v1`) implementing:
`AuthN → AuthZ → Policy → Permission-filtered Retrieval → Model Gateway → Grounded Response → Output Policy`.
PostgreSQL in prod (H2 in tests); async ingestion (Phase 3) + permission-first
hybrid retrieval over local multilingual embeddings (Phase 4).

## Quickstart (dev)

```bash
cp ../.env.example ../.env   # SACHET_JWT_SECRET must be >= 32 chars
mvn test                     # full suite: web, config, auth, RBAC, audit, policy, storage
mvn spring-boot:run          # serves http://localhost:8000/api/v1/health
```

Or `docker compose up --build` (needs a running Docker daemon).

## Layout (actual)

```
src/main/java/ai/sachet/
  SachetApplication.java
  config/   SachetProperties (+Validator fail-fast), SecurityConfig (stateless JWT chain)
  security/ PasswordService (BCrypt-12), JwtService (HS256 fixed), SachetPrincipal,
            JwtAuthFilter, AuthException
  domain/   Classification, User, Role, Permission, Document (+family/version/sha),
            DocumentPermission, AuditLogEntry, IngestionJob (+JobStatus), DocumentChunk
  repo/     Spring Data JPA repositories (incl. permission-filtered findReadable)
  service/  AuthService (login+audit), DocumentAccessService (deny-by-default ACLs),
            AuditService (hash-chained), ModelPolicy (external-transfer gate)
  ingest/   IngestionService (upload/version/list/chunks/jobs/retry), IngestionWorker
            (@Async stage machine incl. EMBEDDING), parsers (PDFBox/POI/TXT-MD),
            TesseractOcrService, ChunkingService (provenance-preserving),
            FileValidationService, MalwareScanner hook (NoOp default, fail-closed when required)
  rag/      EmbeddingService (local MiniLM, pure-Java inference), SafetensorsReader,
            MiniLmEncoder, JvmVectorStore (default) + PgVectorStore (EXPERIMENTAL),
            LikeKeywordSearch, RrfFusion, EvidenceEvaluator, RagService
  storage/  LocalStorageService (traversal-proof, checksummed, quota-guarded)
  web/      HealthController, AuthController, DocumentController, JobController,
            SearchController, ApiExceptionHandler (safe envelopes),
            RequestFilter, Dtos
src/main/resources/ application.yml, db/schema.sql (v2, portable PG+H2), logback-spring.xml
src/test/java/ai/sachet/ 19 test classes — Phases 2–3 suites + local embeddings,
                           fusion/evidence units, pgvector-SQL review, full
                           retrieval isolation/E2E, engine-down 503
```

## First-run note (embeddings)

The first embedding triggers a one-time download (~480MB) into
`SACHET_EMBEDDING_MODEL_DIR` (default `./models/…`, gitignored), then loads
into memory. Ingestion/search calls block only on their own first use;
everything else (health, auth, docs) is unaffected. Absent/broken models fail
as explicit 503 / `EMBEDDING_FAILED`, never silent zeros.

## Security notes

- Algorithm fixed HS256 (constant, not a setting). Placeholder secret ≥32 chars, forbidden outside dev.
- Login failures are generic (no user enumeration). Clearance gate outranks ownership/grants.
- Audit log is hash-chained; `verifyChain()` detects tampering.
- Docs: `docs/API_SPECIFICATION.md`, `docs/DATABASE_SCHEMA.md`, `docs/CONFIGURATION.md`,
  `docs/AUDIT_LOGGING.md`, `docs/DEPLOYMENT.md`, `AGENTS.md`.
