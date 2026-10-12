import { apiFetch, fetchWithStream } from "./client";

export const authApi = {
  login: (email, password) =>
    apiFetch("/auth/login", { method: "POST", body: { email, password } }),
};

export const documentsApi = {
  list: (page = 0, size = 20) => apiFetch(`/documents?page=${page}&size=${size}`),
  get: (id) => apiFetch(`/documents/${id}`),
  create: (formData) => apiFetch(`/documents`, { method: "POST", formData }),
  versions: (id) => apiFetch(`/documents/${id}/versions`),
  addVersion: (id, formData) => apiFetch(`/documents/${id}/versions`, { method: "POST", formData }),
  chunks: (id, page = 0, size = 20) => apiFetch(`/documents/${id}/chunks?page=${page}&size=${size}`),
  delete: (id) => apiFetch(`/documents/${id}`, { method: "DELETE" }),
};

export const jobsApi = {
  get: (id) => apiFetch(`/ingestion/jobs/${id}`),
  retry: (id) => apiFetch(`/ingestion/jobs/${id}/retry`, { method: "POST" }),
};

export const searchApi = {
  search: (query, { topK, docIds, classification, department, owner, from, to } = {}) =>
    apiFetch(`/search`, { method: "POST", body: { query, topK, docIds, classification, department, owner, from, to } }),
};

export const chatApi = {
  ask: (query, { topK, docIds, responseMode, language, customPrompt } = {}) =>
    apiFetch(`/chat`, { 
      method: "POST", 
      body: { query, topK, docIds, responseMode, language, customPrompt } 
    }),
  askStream: (query, { topK, docIds, responseMode, language, customPrompt } = {}) =>
    fetchWithStream(`/chat/stream`, { 
      method: "POST", 
      body: { query, topK, docIds, responseMode, language, customPrompt } 
    }),
};

export const healthApi = {
  health: () => apiFetch(`/health`),
  ready: () => apiFetch(`/ready`),
};
