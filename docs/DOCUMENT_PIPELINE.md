# SACHET AI — Document Pipeline (Phase 0 Spec)

> Status: SPECIFICATION ONLY — NOT IMPLEMENTED.
> Async jobs with visible states; never silently READY.
> Flow: QUEUED → VALIDATING → PARSING → OCR → CHUNKING
> → EMBEDDING → INDEXING → READY | FAILED.

## 1. Upload and Job Creation

- `POST /documents` requires AuthN
  plus the uploader role (or higher).
- The request carries bytes plus metadata:
  `{title, classification, department,
  project_scope, version, language, acl}`.
- The server validates the session,
  classification eligibility, and scope membership.
- It writes `ingestion_jobs = {job_id, doc_id,
  state=QUEUED, stage_detail, attempts, hashes}`.
- Bytes land in private object storage
  under a SHA-256 content hash;
  access is via signed URLs only, never public.

## 2. Validation (VALIDATING)

- MIME allowlist: PDF, scanned-PDF, DOCX,
  PPTX, TXT, MD. Everything else is rejected.
- Size caps (e.g. 200 MB, TBD) and page-count caps apply.
- Policy check: uploader clearance must dominate
  the declared classification.
- Filename sanitization plus zip-bomb guards run here.
- **Malware-scan hook** (NOT IMPLEMENTED):
  a pluggable `MalwareScanner.scan(bytes)` interface
  is defined; the engine choice is TBD.
- An `INFECTED` verdict moves the job to FAILED,
  quarantines the bytes, and raises audit + alert.
- Any check failure yields FAILED with a
  machine-readable `error_code` and a safe user message.

## 3. Classification and Metadata

- Levels: PUBLIC / INTERNAL / RESTRICTED /
  CONFIDENTIAL / SECRET, thresholds configurable.
- Any auto-suggested classifier is advisory only;
  the authorized uploader value is authoritative.
- Metadata and ACLs persist in PostgreSQL:
  `{clearance, departments[], projects[], allowed_roles[]}`.
- Every metadata or ACL change is versioned and audited.
- Classification upgrades need an authorized role.

## 4. Parsing per File Type (PARSING) — Phase 3: IMPLEMENTED (Java)

- **PDF with text layer**: PDFBox — per-page text with page numbers; blank
  pages (<25 chars) flagged for OCR.
- **Scanned PDF / images**: routed to the OCR stage (Tesseract via `OcrService`;
  unavailable → `OCR_UNAVAILABLE`, never silent). Page-image temp files are
  cleaned up; page numbers retained for audit.
- **DOCX**: Apache POI — paragraphs; Heading styles emitted as `#` markers
  so the chunker tracks sections.
- **PPTX**: Apache POI — slides map to pages (slide N → page N); slide
  titles become chunk sections.
- **TXT / MD**: UTF-8 with printable-ratio heuristic; Markdown `#` headers
  structure sections.
- Encrypted or unsupported inputs move to FAILED
  with explicit codes (`PARSE_ERROR` / `VALIDATION_FAILED`);
  partial READY is never emitted.

## 5. OCR With Page Mapping (OCR)

- The OCRGateway fronts Tesseract or an
  enterprise engine (choice TBD, configurable).
- Output per page: `{page, blocks, confidence}`.
- Low-confidence pages are flagged for review.
- A per-document language hint guides engine selection.
- Original page images are retained for audit replay.
- External OCR calls pass the ModelPolicy gate:
  RESTRICTED and above stay on internal engines.
- OCR text inherits the source document classification.

## 6. Structure Detection and Chunking (CHUNKING)

- Detectors find headings, sections, tables,
  lists, and code blocks; headers/footers are dropped.
- Chunks target 800–1200 tokens with 120–150 overlap.
- Each chunk records `{doc_id, version, page, section}`.
- SHA-256 per chunk enables dedup and references.
- Merging across documents or classifications
  in a single chunk is forbidden.

## 7. Embedding, Indexing, READY

- The EmbeddingGateway batches chunks per job;
  the `embedding_model` version is recorded.
- The VectorDBGateway upserts vectors to pgvector
  (HNSW) or Qdrant (optional) with ACL filter fields.
- FTS `tsvector` entries update in the same
  transactional scope where possible.
- READY is set only after count verification
  (stored chunks equal indexed vectors).
- Any mismatch moves the job to FAILED, never READY.

## 8. FAILED Handling and Retry

- All transitions are audited:
  `{job_id, doc_id, stage, attempt, error_code, actor}`
  without storing document bytes in logs.
- Stages retry idempotently with exponential backoff
  (3 attempts, TBD); operators call
  `POST /admin/jobs/{id}/retry`.
- Repeat offenders are quarantined after max attempts
  for manual triage.
- An operator dashboard surfaces FAILED jobs
  with safe, non-sensitive messages (NOT IMPLEMENTED).
