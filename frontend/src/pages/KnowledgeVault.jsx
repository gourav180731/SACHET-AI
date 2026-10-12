import { useState, useCallback } from "react";
import { Link } from "react-router-dom";
import { documentsApi, jobsApi } from "../api";
import {
  Card, UploadZone, Button, IngestionStepper, Select, Input,
  Alert, useToasts, ToastContainer,
} from "../components/UI";

const CLASSIFICATIONS = ["PUBLIC", "INTERNAL", "RESTRICTED", "CONFIDENTIAL", "SECRET"];

const fmtSize = (bytes) => {
  if (!bytes) return "0 B";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
};

export default function KnowledgeVault() {
  const [pending, setPending] = useState([]);
  const [title, setTitle] = useState("");
  const [classification, setClassification] = useState("INTERNAL");
  const [department, setDepartment] = useState("");
  const [uploads, setUploads] = useState([]);
  const [error, setError] = useState(null);
  const [busyFile, setBusyFile] = useState(null);
  const { toasts, push, success, dismiss } = useToasts();

  const onFiles = useCallback((files) => {
    const allowed = /\.(pdf|docx|pptx|txt|md)$/i;
    const accepted = [];
    const rejected = [];
    files.forEach((f) => (allowed.test(f.name) ? accepted : rejected.push(f.name)));
    if (accepted.length) setPending((p) => [...p, ...accepted]);
    if (rejected.length) push(`Unsupported file type skipped: ${rejected.join(", ")}`, "warning", "Some files were skipped");
  }, [push]);

  const removePending = (file) => setPending((p) => p.filter((f) => f !== file));

  const pollJob = useCallback(async (jobId, entry) => {
    for (let i = 0; i < 60; i += 1) {
      await new Promise((r) => window.setTimeout(r, 2000));
      try {
        const job = await jobsApi.get(jobId);
        const next = { ...entry, status: job.status, error: job.errorMessage, attempts: job.attempts };
        setUploads((u) => u.map((x) => (x.jobId === jobId ? next : x)));
        if (job.status === "READY") { success(`${entry.name} is ready to query.`, "Ingestion complete"); break; }
        if (job.status === "FAILED") { push(`${entry.name} failed: ${job.errorMessage || "unknown error"}`, "error", "Ingestion failed"); break; }
      } catch {
        push(`Status polling stopped for ${entry.name}.`, "warning", "Polling interrupted");
        break;
      }
    }
  }, [push, success]);

  const upload = async (file) => {
    setError(null);
    setBusyFile(file.name);
    const fd = new FormData();
    fd.append("file", file);
    if (title) fd.append("title", title);
    fd.append("classification", classification);
    if (department) fd.append("department", department);
    try {
      const res = await documentsApi.create(fd);
      const entry = { name: file.name, documentId: res.documentId, jobId: res.jobId, status: res.status, size: file.size };
      setUploads((u) => [entry, ...u]);
      removePending(file);
      push(`${file.name} accepted for ingestion.`, "success", "Upload started");
      if (res.jobId) pollJob(res.jobId, entry);
    } catch (e) {
      setError(e.message);
      push(e.message, "error", "Upload failed");
    } finally {
      setBusyFile(null);
    }
  };

  const retry = async (jobId) => {
    try {
      const job = await jobsApi.retry(jobId);
      setUploads((u) => u.map((x) => (x.jobId === jobId ? { ...x, status: job.status, error: null } : x)));
      push("Retry requested.", "info");
    } catch (e) {
      push(e.message, "error", "Retry failed");
    }
  };

  const uploadAll = async () => {
    const queue = [...pending];
    for (const f of queue) {
      // eslint-disable-next-line no-await-in-loop
      await upload(f);
    }
    setTitle("");
    setDepartment("");
  };

  return (
    <div className="ui-stack">
      <ToastContainer toasts={toasts} onDismiss={dismiss} />

      <header className="ui-page-head">
        <div>
          <h1 className="ui-page-head__title">Knowledge Vault</h1>
          <p className="ui-page-head__sub">Securely ingest authorized documents into your knowledge workspace.</p>
        </div>
      </header>

      <Card title="Upload secure documents" subtitle="Files are validated, parsed and indexed before they become searchable.">
        {error && <div style={{ marginBottom: "var(--space-4)" }}><Alert kind="error">{error}</Alert></div>}

        <UploadZone onFiles={onFiles} accept=".pdf,.docx,.pptx,.txt,.md" />

        {pending.length > 0 && (
          <div style={{ marginTop: "var(--space-5)" }}>
            <h2 style={{ fontSize: "var(--font-size-sm)", fontWeight: 600, margin: "0 0 var(--space-3)" }}>
              Review before uploading ({pending.length})
            </h2>

            <div className="ui-table-wrap" style={{ marginBottom: "var(--space-4)" }}>
              <table className="ui-table ui-table--compact">
                <thead><tr><th>File</th><th>Size</th><th>Type</th><th aria-label="Remove" /></tr></thead>
                <tbody>
                  {pending.map((f, i) => (
                    <tr key={`${f.name}-${f.lastModified}-${i}`}>
                      <td className="ui-table__title">{f.name}</td>
                      <td className="ui-muted">{fmtSize(f.size)}</td>
                      <td className="ui-muted">{f.type || "—"}</td>
                      <td>
                        <div className="ui-table__actions">
                          <Button variant="ghost" size="sm" onClick={() => removePending(f)} disabled={busyFile === f.name}>Remove</Button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="ui-grid ui-grid--3" style={{ marginBottom: "var(--space-4)" }}>
              <Input label="Title (optional)" value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Defaults to filename" />
              <Select
                label="Classification"
                value={classification}
                onChange={(e) => setClassification(e.target.value)}
                options={CLASSIFICATIONS.map((c) => ({ value: c, label: c }))}
                hint="Sets the default sensitivity for retrieval."
              />
              <Input label="Department (optional)" value={department} onChange={(e) => setDepartment(e.target.value)} placeholder="e.g. Finance" />
            </div>

            <Alert kind="info">
              Files are processed according to your organization&apos;s configured storage and AI transfer policies. Classification is enforced server-side.
            </Alert>

            <div className="ui-row ui-row--end" style={{ marginTop: "var(--space-4)" }}>
              <Button variant="ghost" onClick={() => setPending([])} disabled={!!busyFile}>Discard all</Button>
              <Button variant="primary" onClick={uploadAll} loading={!!busyFile} disabled={!pending.length}>
                {busyFile ? `Uploading ${busyFile}` : `Upload ${pending.length} file(s)`}
              </Button>
            </div>
          </div>
        )}
      </Card>

      <Card title="Ingestion status" subtitle="Live pipeline state for this session">
        {uploads.length === 0 && (
          <p className="ui-muted" style={{ margin: 0, fontSize: "var(--font-size-sm)" }}>
            No uploads in this session.
          </p>
        )}
        {uploads.map((u) => (
          <div key={u.jobId || u.documentId} style={{ padding: "var(--space-3) 0", borderBottom: "1px solid var(--color-border)" }}>
            <div className="ui-row ui-row--between">
              <div style={{ minWidth: 0 }}>
                <strong style={{ fontSize: "var(--font-size-sm)" }}>{u.name}</strong>
                {u.size && <span className="ui-small ui-muted" style={{ marginLeft: 8 }}>{fmtSize(u.size)}</span>}
              </div>
              <div className="ui-row" style={{ gap: "var(--space-2)" }}>
                {u.status === "FAILED" && u.jobId && <Button variant="secondary" size="sm" onClick={() => retry(u.jobId)}>Retry</Button>}
                {u.documentId && <Link className="ui-btn ui-btn--secondary ui-btn--sm" to={`/documents/${u.documentId}`}>Open</Link>}
              </div>
            </div>
            <IngestionStepper status={u.status} errorMessage={u.error} />
          </div>
        ))}
      </Card>
    </div>
  );
}