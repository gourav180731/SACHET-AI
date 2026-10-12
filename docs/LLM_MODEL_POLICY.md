# SACHET AI — LLM Model Policy (Phase 0 Spec)

> Status: SPECIFICATION ONLY — NOT IMPLEMENTED.
> Enforced in PolicyService + ModelGateway; config at `backend/app/core/config.py`.
> Default deny for sensitive tiers; explicit BLOCK beats silent fallback.

## 1. Purpose

- Decide per request which LLM / embedding / STT / TTS / translation / web-search provider may handle content at a given classification.
- Apply before any external network call; violations BLOCK with a named rule, never silently reroute.
- Keep all routing auditable and operator-configurable without code changes.

## 2. Model Tiers

- `internal`: self-hosted / VPC-local models under organizational control (default for sensitive tiers). Model names TBD.
- `approved_external`: allowlisted vendors per capability (e.g., external LLM, external STT/TTS) with DPA + data-residency approval recorded in config.
- `blocked`: no provider allowed for that (classification × capability) cell — request fails closed.
- Embedding models follow the same tiers; mixed-model indexes are rejected by validation.
- Registry shape: `model_policy[{classification}][{capability}] = {tier, allowed_providers[], max_tokens, max_context}`.

## 3. Routing Matrix by Classification (Defaults, Configurable)

| Classification | chat LLM | embedding | STT/TTS | translation | web search |
|---|---|---|---|---|---|
| PUBLIC | internal or approved_external | internal or approved_external | internal or approved_external | internal or approved_external | opt-in tool, external allowed |
| INTERNAL | internal preferred, approved_external allowed | internal preferred | internal or approved_external | internal or approved_external | opt-in, sends summary only |
| RESTRICTED | internal_only (external DENY) | internal_only | internal_only | internal_only | DENY with doc text |
| CONFIDENTIAL | internal_only | internal_only | internal_only | internal_only | DENY |
| SECRET | internal_only, isolated pool | internal_only, isolated pool | local_only, no network | local_only | DENY |

- Operators override via `model_policy.*` keys; SECRET external enable requires SECURITY_ADMIN dual approval + audit.
- Query ceiling = max classification in `security_context` scope and in retrieved chunks; route at the ceiling.

## 4. BLOCK Behavior (No Silent Fallback)

- On forbidden external: raise `ModelPolicyBlocked(rule_id, classification, capability, provider)` → user-facing generic message naming the rule + safe-log detail.
- Forbidden patterns: silent fallback to external when internal fails; silent downgrade to ungrounded answer; retry with a different external not on the allowlist.
- Allowed fallback: external → internal (safe direction) with audit note; internal → external is NEVER automatic.
- Every BLOCK is audited with `{who, when, classification, capability, requested_provider, rule_id}`.

## 5. Token, Context, and Cost Limits

- Per-classification caps: `max_prompt_tokens`, `max_output_tokens`, `max_context_chunks` (e.g., SECRET smaller context window, values TBD in config).
- Retrieval top-k shrinks as classification rises to bound exposure; truncation keeps citations for surviving chunks.
- Rate limits per user/role/classification; SECRET calls throttled and queued.
- Cost/latency headers are internal; never expose provider internals or keys to the client.

## 6. Logging and Audit

- Log `{who, when, classification, capability, provider, model_version, prompt_tokens, output_tokens, latency, doc_ids, pages}`.
- Never log full prompts, chunk text, audio bytes, or keys. Verbose sensitive logging OFF by default.
- Audit log is append-only and access-controlled; SECURITY_ADMIN-only read for SECRET entries.

## 7. Provider Failure Handling

- Internal failure → explicit degradation error ("model unavailable for this classification") + retry with backoff; queue if configured.
- External failure → do NOT escalate classification or widen scope; return explicit error or internal retry if policy allows.
- Embedding/index outage → no ungrounded generation; return refusal + audit `embedding_unavailable`.
- All failures map to safe user errors; stack traces and paths stay in the secure log.

## 8. Config Keys (Centralized)

- `model_policy.tiers`, `model_policy.rules[]`, `model.default_internal`, `model.allowed_external{llm,embedding,stt,tts,translation}`,
- `model.limits[{classification}]`, `model.sensitive_mode` (bool, forces internal_only for RESTRICTED+), `model.web_search_opt_in` (default false).
- `.env.example` holds placeholders only; real keys via secrets manager; CI secret scan must be clean.

## 9. NOT IMPLEMENTED

- Actual model selection, allowlist vendor contracts, dual-approval workflow, and the policy simulation test suite are targets only.
