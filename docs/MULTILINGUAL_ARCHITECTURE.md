# SACHET AI — Multilingual Architecture (Phase 0 Spec)

> Status: SPECIFICATION ONLY — NOT IMPLEMENTED.
> Stack: FastAPI + LanguageService, PostgreSQL + pgvector, TranslationGateway + EmbeddingGateway.
> No fluency guarantees claimed; supported = tested per registry, else EXPERIMENTAL.

## 1. Scope and Principles

- Text multilingual support covers ingestion, retrieval, chat, and Studio generation.
- Voice language handling is specified in `VOICE_ARCHITECTURE.md`; this doc covers text path.
- All 22 scheduled languages share one vector space via multilingual embeddings (model TBD).
- Provenance `{document_id, version, page, section}` survives translation, rerank, and summarization.
- Classification handling rules are configurable; translation never downgrades classification.
- Unsupported or untested language pairs are labeled `EXPERIMENTAL` and audited.

## 2. 22 Scheduled Languages (Configurable Registry)

- Registry key: `languages.supported[]` in `backend/app/core/config.py` (default list below, operator-overridable).
- List: Assamese (as), Bengali (bn), Bodo (brx), Dogri (doi), Gujarati (gu), Hindi (hi),
  Kannada (kn), Kashmiri (ks), Konkani (kok), Maithili (mai), Malayalam (ml), Manipuri (mni),
  Marathi (mr), Nepali (ne), Odia (or), Punjabi (pa), Sanskrit (sa), Santali (sat),
  Sindhi (sd), Tamil (ta), Telugu (te), Urdu (ur), plus English (en) as interop language.
- Each entry: `{code, name, script[], embedding_supported, translation_pairs, stt_supported, tts_supported, status: SUPPORTED | EXPERIMENTAL | NOT IMPLEMENTED}`.
- Adding/removing a language is config + test change, never code fork.

## 3. Language Detection

- Service: `LanguageService.detect(text)` behind `TranslationGateway` abstraction.
- Stage 1: fast local detector (CLD3/fastText-style, TBD) for query + per chunk at ingestion.
- Stage 2: confirmation via embedding-space signal or operator-approved external detector only if ModelPolicy allows classification.
- Detection output: `{language_code, script, confidence_band: high|low, mixed: bool}` — no numeric fluency % exposed to users.
- Low-confidence or mixed input routes to code-switching path (§6), never silent default to Hindi/English.
- Ingestion stores `chunk.language` + `chunk.script`; queries carry `query.language` into RetrievalService.

## 4. Routing and Retrieval

- Flow: `User → AuthN → AuthZ → Security Policy → LanguageService.detect → Permission-filtered Retrieval → Model Gateway → Output Policy`.
- Retrieval is language-aware hybrid: pgvector cosine (multilingual embedding) fused with PostgreSQL FTS using per-language `tsvector` configs where available, fallback to `simple` config otherwise.
- Cross-lingual retrieval: query embedded in shared space; ACL + classification predicates apply BEFORE similarity scoring.
- Reranker (target, NOT IMPLEMENTED) must be multilingual or disabled for non-English; disabling is explicit config, not silent skip.
- No translation of the full corpus at index time; translate-on-display only, with source chunk retained.

## 5. Translation Path

- Interface: `TranslationGateway.translate(text, src, tgt, classification, security_context)` — checks ModelPolicy before any external call.
- Internal translation preferred for RESTRICTED and above (default DENY external; see `LLM_MODEL_POLICY.md`).
- Translation preserves citations: translated answer cites original `{doc_id, version, page, section}` plus `translated_from: <lang>`.
- Chunk-level translation for display keeps original text available side-by-side; audit logs record `{src, tgt, provider, classification}` without storing full content.
- Glossary/domain terms (designations, scheme names) use protected-term list to prevent mistranslation; list is config-driven.

## 6. Code-Switching and Mixed Script

- Hinglish and mixed-script queries (e.g., Roman Hindi + Devanagari, Tamil + English) are first-class inputs.
- Handling: normalize script variants → detect segments per sentence → embed jointly; do not force single-language rewrite.
- Transliteration is a display aid only, never a retrieval key replacement.
- If segments disagree on language, `mixed: true` flag propagates to prompt so the model answers in the user's dominant language and names its choice.
- Tests must include code-switched fixtures; untested pairs stay EXPERIMENTAL.

## 7. Fallback Behavior

- Order: requested language → detected dominant language → English glosszahra side-by-side → explicit insufficient-evidence message.
- Never fabricate an answer in a language with no authorized evidence.
- If translation provider is blocked by policy or down: return source-language grounded answer + explicit notice naming the block reason.
- If embedding model lacks a language: mark retrieval `limited`, exclude untested vectors, audit the event.

## 8. Testing Methodology (NOT IMPLEMENTED)

- Per-language fixtures: ingestion → retrieval → answer → translation round-trip, each asserting citation preservation.
- Metrics: retrieval recall@k per language, citation integrity rate, language-match rate, block-rate for forbidden external calls. No human-fluency scores claimed without audited native-speaker eval.
- Security tests: cross-user leak via translated paraphrase, unauthorized-language bypass, script-spoofing (homoglyph) handling.
- Acceptance: language marked SUPPORTED only after fixtures + security tests pass on pgvector default path.

## 9. NOT IMPLEMENTED in Phase 0

- Embedding model choice, FTS dictionaries for all 22 languages, external translation provider selection, transliteration engine, and the full eval harness are interfaces and targets only.
