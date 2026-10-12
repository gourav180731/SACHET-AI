# SACHET AI — RAG Architecture (Phase 4: IMPLEMENTED)

> Status: retrieval implemented and tested (110 green tests).
> Embeddings: local paraphrase-multilingual-MiniLM-L12-v2 (384 dims), pure-Java
> inference over safetensors (no native ML libs, no external calls).
> Default store: JVM cosine over permission-first candidates. pgvector HNSW is
> EXPERIMENTAL (code + predicate-reviewed SQL; enable after operator DDL below).

## 1. Ingestion-to-Index Overview

The pipeline flows:
Upload → VALIDATING → PARSING → OCR → CHUNKING
→ EMBEDDING → INDEXING → READY | FAILED.
Each chunk record stores
`{chunk_id, doc_id, version, page, section,
language, classification, acl_snapshot_ref,
hash, embedding_model}`.
Provenance fields are immutable once written.
A document version bump triggers reindexing,
never in-place mutation of history.

## 2. Chunking (800–1200 Tokens + Overlap)

- Target size is ~1000 tokens
  with 120–150 tokens of overlap.
- Splits respect section and page boundaries;
  tables and lists stay atomic where possible.
- Never merge content across documents
  or across classifications in one chunk.
- Headers and footers are excluded from chunks.
- Each chunk inherits `{doc_id, version,
  page, section, classification}` from its source.
- SHA-256 hashing supports dedup
  and tamper-evidence references.

## 3. Embeddings (Multilingual) — IMPLEMENTED

- `EmbeddingService` fronts the local model:
  `sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2`
  (revision `e8f8c21…`, 384 dims, Apache-2.0).
- Inference is pure Java (exact HF tokenization + hand-written transformer
  over safetensors; no ONNX Runtime — its native DLL cannot initialize on
  hosts carrying an inbox `System32\onnxruntime.dll`, verified empirically).
- No document/query text ever leaves the machine for embedding; there is no
  external embedding provider to misconfigure.
- Model files (`model.safetensors` + `tokenizer.json`) download once into
  `sachet.embedding-model-dir` (gitignored `./models/…`); missing files fail
  with explicit 503, never silent zeros.
- A single vector space covers English plus the scheduled Indian languages
  (Hindi verified in tests; full registry evaluation is Phase 8).

## 4. Retrieval (Hybrid, Permission-First) — IMPLEMENTED

- **Step 0 — policy filter**: `security_context` compiles to clearance bands +
  owner/grant predicates. The filter applies BEFORE similarity search,
  so unauthorized vectors are never scored (cross-user tests prove zero leakage).
- **Step 1 — hybrid search**: JVM cosine over authorized candidates
  (keyword-matching first, recent fill to the candidate cap) fused with
  portable LIKE lexical ranking via reciprocal-rank fusion (k=60, no tuned weights).
- **pgvector HNSW (EXPERIMENTAL)**: `PgVectorStore` runs the same pre-filter
  natively (`embedding_vec <=> :qvec`, HNSW `vector_cosine_ops`); selected only
  when `sachet.vector-store=pgvector` AND the extension + column probe passes,
  otherwise loud fallback to JVM. Operator DDL:
  `CREATE EXTENSION vector; ALTER TABLE document_chunks ADD COLUMN embedding_vec vector(384);`
  `CREATE INDEX idx_chunks_vec … USING hnsw (embedding_vec vector_cosine_ops);`
  plus a backfill from the portable `embedding` column.
- **Step 2 — fusion is the rerank** (cross-encoder is a future upgrade, NOT IMPLEMENTED).
- **Step 3 — citation attach**: every result keeps `{documentId, title,
  version, page, section, vectorScore}`; fusion never drops fields.

## 5. Evidence Status Scale — IMPLEMENTED (`EvidenceEvaluator`)

Documented heuristics on the best cosine similarity (configurable
`evidence-strong/moderate/limited` = 0.70/0.50/0.30), never fake confidence %:

- `STRONG` / `MODERATE` / `LIMITED` per the bands above (message null).
- `INSUFFICIENT`: no authorized evidence found —
  the system replies exactly:
  "I could not find sufficient supporting information
  in the authorized documents."
- Fabricating citations or confidence percentages
  is forbidden. Status and sources render in the UI.

## 6. Version and Freshness Handling

- Document versions are immutable;
  `supersedes` links chain old to new.
- Default search boosts the effective-latest version.
- Superseded hits are labeled
  "SUPERSEDED — see vN" and deprioritized.
- Circular and manual updates propagate
  through re-ingest jobs, not edits.
- Stale embeddings are quarantined
  until reindexed under the new version.

## 7. Failure Behavior

- Embedding or index outage yields
  an explicit degradation error —
  never ungrounded generation from parametric memory.
- Partially indexed documents stay non-READY
  and are excluded from retrieval entirely.
- Every failure is audited with
  `{job_id, stage, error_code, attempt}`.

## 8. NOT IMPLEMENTED in Phase 0

Embedding model selection, HNSW tuning
(`m`, `ef_construction`), FTS dictionaries
for 22 languages, reranker choice,
and the evaluation harness
(accuracy, citation, version, and filter tests)
exist as interfaces and targets only.
