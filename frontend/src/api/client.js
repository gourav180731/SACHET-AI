const API_BASE = import.meta.env.VITE_API_BASE || "http://localhost:8000/api/v1";

let token = null;
export function setToken(t) { token = t; }
export function getToken() { return token; }

export class ApiError extends Error {
  constructor(status, code, message) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

function requestId() {
  return (crypto?.randomUUID?.() || String(Date.now() + Math.random())).slice(0, 36);
}

export async function apiFetch(path, { method = "GET", body, formData } = {}) {
  const headers = { "X-Request-ID": requestId() };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers["Content-Type"] = "application/json";
  let res;
  try {
    res = await fetch(`${API_BASE}${path}`, {
      method,
      headers,
      body: formData ?? (body !== undefined ? JSON.stringify(body) : undefined),
    });
  } catch {
    throw new ApiError(0, "NETWORK", "SACHET AI service is temporarily unavailable.");
  }
  if (res.status === 401) {
    window.dispatchEvent(new Event("sachet:unauthorized"));
    throw new ApiError(401, "AUTH", "Your session has expired. Please sign in again.");
  }
  if (res.status === 403) throw new ApiError(403, "FORBIDDEN", "You do not have permission to access this resource.");
  if (res.status === 404) throw new ApiError(404, "NOT_FOUND", "The requested resource was not found.");
  const text = await res.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = null; }
  if (!res.ok) {
    const msg = data?.error?.message || "The request could not be completed.";
    throw new ApiError(res.status, data?.error?.code || "ERROR", msg);
  }
  return data;
}

export async function apiDownload(path, filename) {
  const headers = { "X-Request-ID": requestId() };
  if (token) headers.Authorization = `Bearer ${token}`;
  let res;
  try {
    res = await fetch(`${API_BASE}${path}`, { headers });
  } catch {
    throw new ApiError(0, "NETWORK", "SACHET AI service is temporarily unavailable.");
  }
  if (res.status === 401) {
    window.dispatchEvent(new Event("sachet:unauthorized"));
    throw new ApiError(401, "AUTH", "Your session has expired. Please sign in again.");
  }
  if (res.status === 403) throw new ApiError(403, "FORBIDDEN", "You do not have permission to access this resource.");
  if (res.status === 404) throw new ApiError(404, "NOT_FOUND", "The requested resource was not found.");
  if (!res.ok) throw new ApiError(res.status, "ERROR", "Download failed.");
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

export { API_BASE };

export async function* fetchWithStream(path, { method = "POST", body } = {}) {
  const headers = { 
    "X-Request-ID": requestId(),
    "Accept": "text/event-stream",
    "Content-Type": "application/json"
  };
  if (token) headers.Authorization = `Bearer ${token}`;
  
  let res;
  try {
    res = await fetch(`${API_BASE}${path}`, { method, headers, body: JSON.stringify(body) });
  } catch {
    throw new ApiError(0, "NETWORK", "SACHET AI service is temporarily unavailable.");
  }
  
  if (res.status === 401) {
    window.dispatchEvent(new Event("sachet:unauthorized"));
    throw new ApiError(401, "AUTH", "Your session has expired. Please sign in again.");
  }
  if (res.status === 403) throw new ApiError(403, "FORBIDDEN", "You do not have permission to access this resource.");
  if (res.status === 404) throw new ApiError(404, "NOT_FOUND", "The requested resource was not found.");
  if (!res.ok) {
    const text = await res.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch { data = null; }
    const msg = data?.error?.message || "Streaming request failed.";
    throw new ApiError(res.status, data?.error?.code || "ERROR", msg);
  }

  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      
      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split("\n");
      buffer = lines.pop() || "";
      
      for (const line of lines) {
        if (line.startsWith("data:")) {
          const data = line.startsWith("data: ") ? line.slice(6) : line.slice(5);
          if (data === "[DONE]") return;
          yield data;
        }
      }
    }
    if (buffer.startsWith("data:")) {
      const data = buffer.startsWith("data: ") ? buffer.slice(6) : buffer.slice(5);
      if (data !== "[DONE]") yield data;
    }
  } finally {
    reader.releaseLock();
  }
}
