# FRONTEND ↔ BACKEND API MAPPING

| Frontend Feature | Endpoint | HTTP Method | Request | Response | Authentication | Authorization | Current Status |
|---|---|---|---|---|---|---|---|
| Login | /api/v1/auth/login | POST | `{email, password}` | `{tokenType, accessToken, roles}` | Public | — | IMPLEMENTED |
| Health check | /api/v1/health | GET | — | `{status, version}` | Public | — | IMPLEMENTED |
| Readiness | /api/v1/ready | GET | — | `{status}` / 503 | Public | — | IMPLEMENTED |
| Document list | /api/v1/documents?page=&size= | GET | — | `PageResponse<DocumentResponse>` | JWT | Server-side RBAC/doc ACL | IMPLEMENTED |
| Document detail | /api/v1/documents/{id} | GET | — | `DocumentResponse` | JWT | 404 hides existence | IMPLEMENTED |
| Document versions | /api/v1/documents/{id}/versions | GET, POST (multipart `file`) | — | `DocumentResponse[]` / `SubmitResponse` | JWT | ACL | IMPLEMENTED |
| Document chunks | /api/v1/documents/{id}/chunks | GET | — | `PageResponse<ChunkResponse>` | JWT | ACL | IMPLEMENTED |
| Document download | /api/v1/documents/{id}/download | GET | — | attachment | JWT | ACL + audit | IMPLEMENTED (Phase 6) |
| Upload | /api/v1/documents | POST (multipart `file`, `title`, `classification`, `department`) | — | 202/200 `SubmitResponse` | JWT | ACL | IMPLEMENTED |
| Ingestion job status | /api/v1/ingestion/jobs/{id} | GET | — | `JobResponse` | JWT | ACL (owner) | IMPLEMENTED |
| Ingestion retry | /api/v1/ingestion/jobs/{id}/retry | POST | — | `JobResponse` (202) | JWT | ACL | IMPLEMENTED |
| RAG search | /api/v1/search | POST | `{query, topK?, docIds?}` | `{results[], evidenceStatus, message}` | JWT | Permission-filtered | IMPLEMENTED |
| AI chat | /api/v1/chat | POST | `{query, docIds?, topK?}` | `{answer, evidenceStatus, message, sources[]}` | JWT | Permission-filtered | IMPLEMENTED |
| Audit trail | /api/v1/audit?page=&size= | GET | — | `PageResponse<AuditEntryView>` | JWT | `@PreAuthorize("hasAnyRole('ADMIN','SECURITY_ADMIN')")` | IMPLEMENTED (Phase 5 backend) |
| Studio — executive summary | /api/v1/studio/executive-summary | POST | `{query, docIds?, topK?}` | `{output, sources}` | JWT | Permission-filtered via RagService | IMPLEMENTED (Phase 5 backend) |
| Studio — detailed summary | /api/v1/studio/detailed-summary | POST | same | same | JWT | Permission-filtered | IMPLEMENTED (Phase 5 backend) |
| Studio — key points | /api/v1/studio/key-points | POST | same | same | JWT | Permission-filtered | IMPLEMENTED (Phase 5 backend) |
| Studio — entities | /api/v1/studio/entities | POST | same | same | JWT | Permission-filtered | IMPLEMENTED (Phase 5 backend) |
| Studio — action items | /api/v1/studio/action-items | POST | same | same | JWT | Permission-filtered | IMPLEMENTED (Phase 5 backend) |
| Studio — translate | /api/v1/studio/translate | POST | same + `targetLang` | same | JWT | Permission-filtered | IMPLEMENTED (Phase 5 backend) |
| Studio — explain | /api/v1/studio/explain | POST | `{topic, docIds?}` | same | JWT | Permission-filtered | IMPLEMENTED (Phase 5 backend) |
| Studio — compare | /api/v1/studio/compare | POST | `{topic, docIdA, docIdB}` | same | JWT | Permission-filtered | IMPLEMENTED (Phase 5 backend) |
| Studio — generate DOCX | /api/v1/studio/generate/docx | POST | `{title, topic, docIds, topK}` | `ArtifactView` | JWT | Permission-filtered via RagService | IMPLEMENTED (Phase 6) |
| Studio — generate PPTX | /api/v1/studio/generate/pptx | POST | `{title, topic, docIds, topK, slides}` | `ArtifactView` | JWT | Permission-filtered via RagService | IMPLEMENTED (Phase 6) |
| Generated files list | /api/v1/artifacts?page=&size= | GET | — | `PageResponse<ArtifactView>` | JWT | Owner/admin/source-access | IMPLEMENTED (Phase 6) |
| Artifact detail | /api/v1/artifacts/{id} | GET | — | `ArtifactView` | JWT | Owner/admin/source-access; 404 hides | IMPLEMENTED (Phase 6) |
| Artifact download | /api/v1/artifacts/{id}/download | GET | — | attachment | JWT | Owner/admin/source-access + audit | IMPLEMENTED (Phase 6) |
| Artifact delete | /api/v1/artifacts/{id} | DELETE | — | 204 | JWT | Owner/admin only + audit | IMPLEMENTED (Phase 6) |
| Current user | /api/v1/auth/me | GET | — | `{id,email,clearance,department,roles}` | JWT | Own principal | IMPLEMENTED (Phase 6) |
| RAG search + filters | /api/v1/search | POST | `{query, topK?, docIds?, classification?, department?, owner?, from?, to?}` | `{results[], evidenceStatus, message}` | JWT | Permission-filtered, then filters | IMPLEMENTED (Phase 6) |
| Voice capabilities | /api/v1/voice/capabilities | GET | — | `{voiceEnabled, sttLanguages, ttsLanguages, sttAvailable, ttsAvailable, wakeWordAvailable, wakeWords[], note}` | JWT | Clearance check | IMPLEMENTED (Phase 6) |
| Voice wake word / STT/TTS | Web Speech API (browser) | — | audio (mic) | text (transcribed) / audio (TTS) | — | Browser permission + JWT for chat | IMPLEMENTED (Phase 6 — on-device) |
| Exports / audit admin / security policy API | — | — | — | — | — | — | NOT AVAILABLE |

Security invariants: every sensitive endpoint requires JWT; retrieval is permission-filtered server-side before any model call; audit rows expose metadata only (no document text, tokens, or passwords); 404 hides document existence; generic JSON errors only.

