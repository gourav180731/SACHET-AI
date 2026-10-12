# SACHET AI — Project Vision (Phase 0 Spec)

> Status: SPECIFICATION ONLY — NOT IMPLEMENTED.
> No operational features claimed in Phase 0.
> Authority: AGENTS.md (highest authority).
> Priority: Privacy > Security > Authorization > Grounded RAG > Multilingual.

## 1. What SACHET AI Is

SACHET AI (Secure Access to Centralized Heritage
of Enterprise Texts with AI) is a security-first,
multilingual, source-grounded document intelligence agent.
It serves government and organizational users
working with sensitive internal knowledge.
It helps authorized users ask questions, summarize,
translate, and generate reports.
It answers only from documents the user is permitted to see.
Every grounded answer traces to
`{document_id, version, page, section}`.
If evidence is insufficient, it refuses with a standard sentence
instead of hallucinating.

## 2. Why It Exists

Organizational knowledge (C-DOT context) is fragmented
across PDFs, scanned binders, DOCX files, PPTX decks,
TXT/MD notes, circulars, and SOP manuals.
General-purpose chatbots leak across clearance boundaries,
hallucinate procedures, and ignore version authority.
SACHET AI exists to give useful answers
without violating need-to-know.
It adds multilingual access (22 scheduled languages + English),
including a voice path for field staff.
Security and privacy are never traded for convenience.

## 3. C-DOT Priority Use Cases

1. **CAP alerts**: staff query Common Alerting Protocol formats,
   dissemination SOPs, and escalation matrices.
   Answers cite the exact SOP version and page.
2. **Telecom SOPs / O&M manuals**: field engineers ask procedure steps.
   The system returns grounded steps plus warnings.
   It never invents CLI commands or parameters.
3. **Circulars / office orders**: HR and admin queries
   (e.g. "leave rule as per circular 2024-12?")
   resolve to the exact effective circular version.
   Superseded versions are flagged, never silently served.
4. **Technical manuals / R&D docs**: project-scoped retrieval
   keeps Team-A documents invisible to Team-B.
   Cross-project leakage is blocked pre-retrieval.
5. **Multilingual access**: Hindi, Tamil, Bengali and other
   scheduled-language queries are answered from English sources
   with provenance preserved through translation.
6. **Voice access (NOT IMPLEMENTED)**: hands-free query
   via the Voice Gateway for field use.
   Phase 0 defines interfaces and policy gates only.

## 4. Non-Goals (Phase 0)

- No autonomous external actions
  (ticketing, network config, messaging). NOT IMPLEMENTED.
- No public web search by default.
  Web is an opt-in tool and never receives raw doc text.
- No cross-tenant or cross-department bypass modes.
- No "admin sees everything without audit" mode.
- No merging of Jarvis / Spike repos.
  Those are reference-only for voice/session patterns.
- No "100% secure / unhackable" claims.
  Controls are documented with limitations and tests.

## 5. Security + Multilingual Philosophy

- Deny by default: unauthenticated users get no retrieval;
  unauthorized documents are invisible to the LLM.
- Strict separation: SYSTEM ≠ USER ≠ DOCUMENT_CONTENT ≠ TOOL_OUTPUT.
  Documents are untrusted data and never override policy.
- Provenance always survives translation,
  summarization, reranking, and caching.
- No silent fallback: if ModelPolicy + classification
  forbids an external call, the system BLOCKS with an explicit error.
- Multilingualism is access, not risk:
  the Language Gateway normalizes queries,
  and answers are labeled INTERNAL KNOWLEDGE vs EXTERNAL WEB.

## 6. Long-Term Vision

- Phase 1: AuthN/AuthZ + ingestion pipeline
  + permission-filtered RAG + audit (core slice).
- Phase 2: Studio (report/PPT generation),
  risk-gated agent tools, voice MVP.
- Phase 3: hardened deployment
  (KMS/HSM, WAF, SIEM export), red-team tested,
  with evaluated multilingual RAG quality.
- North star: any answer can be replayed by an auditor
  to its sources, policy checks, and model version,
  and any authorized user can work in their own language
  by voice or text without touching unauthorized data.

## 7. Acceptance for This Document

This vision is accepted when stakeholders agree
on the use cases (§3), non-goals (§4),
and the AGENTS.md priority order,
and when every downstream spec traces its requirements
to these use cases.
No code is required in Phase 0.
