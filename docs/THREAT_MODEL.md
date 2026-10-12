# SACHET AI — Threat Model (Phase 0 Spec)

> Status: SPECIFICATION ONLY.
> Mitigations and tests are targets — NOT IMPLEMENTED.
> Method: STRIDE-inspired, one row per threat.
> Each control needs code + test + audit before claiming it.

## Threat Table: Threat → Impact → Mitigation → Test

| # | Threat | Impact |
|---|---|---|
| T1 | Unauthorized access via IDOR or guessed doc_id | Confidentiality breach of sensitive docs |
| T2 | Credential theft or session hijack | Account takeover, privilege abuse |
| T3 | Prompt injection inside docs ("ignore instructions") | Policy bypass, false or malicious output |
| T4 | RAG poisoning via planted false SOP version | Wrong procedures, operational safety risk |
| T5 | Cross-user leakage via shared chunks or cache | Need-to-know violation between users |
| T6 | Model leakage of sensitive text to external LLM/STT | Egress breach to third-party provider |
| T7 | External API leakage via web search carrying doc text | Exfiltration to search engines |
| T8 | Insecure export or share bypassing approval | Uncontrolled spread of SECRET material |
| T9 | Malicious or over-privileged agent tool action | Destructive writes, credential access |
| T10 | Insider abuse reading docs without trace | Undetected snooping by privileged staff |
| T11 | Accidental sharing from wrong classification/scope | Over-exposure to unauthorized groups |
| T12 | Dependency or model-supply-chain compromise | RCE, backdoors, poisoned components |

## Mitigations (Specification Targets)

- **M1 (T1)**: server-side AuthZ on every endpoint
  and retrieval call; direct-object checks;
  client-supplied roles ignored entirely.
- **M2 (T2)**: short-lived JWTs with rotation,
  MFA target, Argon2 hashing, rate limits,
  httpOnly secure cookies.
- **M3 (T3)**: prompt delimiting with hierarchy
  SYSTEM > USER > DOCUMENT > TOOL;
  documents treated as data; output validation.
- **M4 (T4)**: version authority with `supersedes` links,
  uploader allowlists, content hashes,
  approval for SECRET ingest (target).
- **M5 (T5)**: pre-retrieval ACL filtering;
  per-scope cache keys; LLM receives filtered hits only.
- **M6 (T6)**: ModelPolicy + classification gate
  before any external call; BLOCK, never silent fallback.
- **M7 (T7)**: web search as opt-in tool only;
  query substrings, never bulk chunk text; user-confirmed.
- **M8 (T8)**: HIGH-risk gating — confirmation,
  reason capture, watermark, label, and audit event.
- **M9 (T9)**: four tool risk tiers
  (LOW/MEDIUM/HIGH/CRITICAL);
  HIGH+ needs confirmation + audit;
  CRITICAL targets dual approval.
- **M10 (T10)**: admin reads audited;
  separate auditor role; append-only tamper-evident logs.
- **M11 (T11)**: default-deny upload flow;
  classification review step; scope picker;
  effective-permission preview before publish.
- **M12 (T12)**: pinned dependencies, SBOM,
  image signing, secret scans, provider allowlist.

## Required Tests (Must Pass Before Claiming Control)

- Cross-user fetch: user-B gets 403
  on user-A RESTRICTED documents (T1).
- Login brute-force and token-replay resistance (T2).
- Injection corpus: malicious docs never alter
  tool or AuthZ behavior (T3).
- Poisoned-corpus eval: rogue versions flagged
  or refused, never silently served (T4).
- Cache isolation: identical query by two users
  yields disjoint citations per ACL (T5).
- Egress test: RESTRICTED+ queries assert
  zero external calls via mocked gateway (T6/T7).
- Export bypass: direct API export without
  confirmation returns 403 plus an audit event (T8).
- Tool escalation: LOW tools cannot reach
  HIGH/CRITICAL paths (T9).
- Audit tamper: log edits are detected;
  admin reads leave immutable events (T10).
- Mis-scoped upload is immediately invisible
  to out-of-scope users (T11).
- CI: dependency audit and secret scan clean;
  unsigned images rejected (T12).

## Residual Risk Statement

Residual risk is never zero.
Documentation must state limitations
instead of claiming "100% secure".
The red-team suite covering injection,
escalation, export bypass, and leakage probes
is NOT IMPLEMENTED in Phase 0;
this file is its specification.
