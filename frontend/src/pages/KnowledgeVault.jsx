import { useState, useCallback, useEffect } from "react";
import { Link } from "react-router-dom";
import { documentsApi, jobsApi } from "../api";
import {
  Card, UploadZone, Button, DeleteButton, Tooltip, IngestionStepper, Select, Input,
  Alert, useToasts, ToastContainer, StatusBadge, ClassificationBadge,
} from "../components/UI";

const CLASSIFICATIONS = ["PUBLIC", "INTERNAL", "RESTRICTED", "CONFIDENTIAL", "SECRET"];

const ALLOWED_EXTS = ["pdf", "doc", "docx", "ppt", "pptx", "txt", "md", "csv", "rtf", "html", "htm", "json", "xml"];
const ALLOWED_REGEX = new RegExp(`\\.(${ALLOWED_EXTS.join("|")})$`, "i");

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
  const [autoUpload, setAutoUpload] = useState(true);
  const [uploads, setUploads] = useState([]);
  const [recentDocs, setRecentDocs] = useState([]);
  const [loadingDocs, setLoadingDocs] = useState(false);
  const [error, setError] = useState(null);
  const [busyFile, setBusyFile] = useState(null);
  const { toasts, push, success, dismiss } = useToasts();

  const loadRecentDocs = useCallback(async () => {
    setLoadingDocs(true);
    try {
      const res = await documentsApi.list(0, 5);
      if (res?.content) {
        setRecentDocs(res.content);
      }
    } catch {
      // non-critical if list fails
    } finally {
      setLoadingDocs(false);
    }
  }, []);

  useEffect(() => {
    loadRecentDocs();
  }, [loadRecentDocs]);

  const pollJob = useCallback(async (jobId, entry) => {
    for (let i = 0; i < 60; i += 1) {
      await new Promise((r) => window.setTimeout(r, 2000));
      try {
        const job = await jobsApi.get(jobId);
        const next = { ...entry, status: job.status, error: job.errorMessage, attempts: job.attempts };
        setUploads((u) => u.map((x) => (x.jobId === jobId ? next : x)));
        if (job.status === "READY") {
          success(`${entry.name} is parsed, indexed, and ready to query.`, "Ingestion complete");
          loadRecentDocs();
          break;
        }
        if (job.status === "FAILED") {
          push(`${entry.name} ingestion failed: ${job.errorMessage || "unknown error"}`, "error", "Ingestion failed");
          break;
        }
      } catch {
        push(`Status polling stopped for ${entry.name}.`, "warning", "Polling interrupted");
        break;
      }
    }
  }, [push, success, loadRecentDocs]);

  const uploadSingle = useCallback(async (file, customTitle = null) => {
    setError(null);
    setBusyFile(file.name);
    const fd = new FormData();
    fd.append("file", file);
    if (customTitle || title) fd.append("title", customTitle || title);
    fd.append("classification", classification);
    if (department) fd.append("department", department);

    try {
      const res = await documentsApi.create(fd);
      const isDup = !!res.duplicate;
      const entry = {
        name: file.name,
        documentId: res.documentId,
        jobId: res.jobId,
        status: res.status || (isDup ? "READY" : "QUEUED"),
        size: file.size,
        duplicate: isDup,
        time: new Date().toLocaleTimeString(),
      };
      setUploads((u) => [entry, ...u]);
      setPending((p) => p.filter((f) => f !== file));

      if (isDup) {
        success(`${file.name} is already indexed in vault. Ready to query.`, "Document ready");
        loadRecentDocs();
      } else {
        push(`${file.name} uploaded successfully. Ingestion in progress...`, "info", "Upload started");
        if (res.jobId) {
          pollJob(res.jobId, entry);
        }
      }
      return true;
    } catch (e) {
      const msg = e.message || "Failed to upload file.";
      setError(`${file.name}: ${msg}`);
      push(msg, "error", `Upload failed (${file.name})`);
      return false;
    } finally {
      setBusyFile(null);
    }
  }, [title, classification, department, pollJob, push, success, loadRecentDocs]);

  const onFiles = useCallback((files) => {
    setError(null);
    const accepted = [];
    const rejected = [];

    files.forEach((f) => {
      if (ALLOWED_REGEX.test(f.name)) {
        accepted.push(f);
      } else {
        rejected.push(f.name);
      }
    });

    if (rejected.length) {
      push(
        `Unsupported files skipped: ${rejected.join(", ")}. Supported: PDF, DOC, DOCX, PPT, PPTX, TXT, MD, CSV, RTF, HTML, JSON, XML.`,
        "warning",
        "Some files were skipped"
      );
    }

    if (!accepted.length) return;

    if (autoUpload) {
      // Direct auto-upload mode: process files sequentially
      (async () => {
        for (const file of accepted) {
          // eslint-disable-next-line no-await-in-loop
          await uploadSingle(file);
        }
      })();
    } else {
      setPending((p) => [...p, ...accepted]);
      push(`${accepted.length} file(s) staged for review. Click "Upload All" to ingest.`, "info", "Files added");
    }
  }, [autoUpload, uploadSingle, push]);

  const removePending = (file) => setPending((p) => p.filter((f) => f !== file));

  const uploadAll = async () => {
    const queue = [...pending];
    for (const f of queue) {
      // eslint-disable-next-line no-await-in-loop
      await uploadSingle(f);
    }
    setTitle("");
  };

  const retry = async (jobId) => {
    try {
      const job = await jobsApi.retry(jobId);
      setUploads((u) => u.map((x) => (x.jobId === jobId ? { ...x, status: job.status, error: null } : x)));
      push("Ingestion retry requested.", "info");
      const target = uploads.find((x) => x.jobId === jobId);
      if (target) pollJob(jobId, target);
    } catch (e) {
      push(e.message, "error", "Retry failed");
    }
  };

  return (
    <div className="ui-stack" style={{ gap: "var(--space-6)" }}>
      <ToastContainer toasts={toasts} onDismiss={dismiss} />

      <header className="ui-page-head">
        <div>
          <h1 className="ui-page-head__title">Knowledge Vault</h1>
          <p className="ui-page-head__sub">
            Secure multi-format document intake with verified source grounding, RBAC isolation, and instant semantic indexing.
          </p>
        </div>
      </header>

      {/* Upload Zone Card */}
      <Card
        title="Ingest Documents"
        subtitle="Supports PDF, DOC, DOCX, PPT, PPTX, TXT, MD, CSV, RTF, HTML, JSON, and XML."
      >
        {error && (
          <div style={{ marginBottom: "var(--space-4)" }}>
            <Alert kind="error">{error}</Alert>
          </div>
        )}

        {/* Ingestion Parameters Toolbar */}
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))",
            gap: "var(--space-3)",
            marginBottom: "var(--space-4)",
            padding: "var(--space-3)",
            background: "rgba(255, 255, 255, 0.03)",
            borderRadius: "var(--radius-md)",
            border: "1px solid var(--color-border)",
          }}
        >
          <Select
            label="Classification"
            value={classification}
            onChange={(e) => setClassification(e.target.value)}
            options={CLASSIFICATIONS.map((c) => ({ value: c, label: c }))}
            hint="Mandatory RBAC security tag"
          />
          <Input
            label="Department (optional)"
            value={department}
            onChange={(e) => setDepartment(e.target.value)}
            placeholder="e.g. Operations, IT, Finance"
          />
          <div style={{ display: "flex", flexDirection: "column", justifyContent: "center", paddingTop: "14px" }}>
            <label
              style={{
                display: "flex",
                alignItems: "center",
                gap: "8px",
                fontSize: "var(--font-size-sm)",
                cursor: "pointer",
                userSelect: "none",
                fontWeight: 500,
              }}
            >
              <input
                type="checkbox"
                checked={autoUpload}
                onChange={(e) => setAutoUpload(e.target.checked)}
                style={{ accentColor: "var(--color-primary, #6366f1)", width: "16px", height: "16px" }}
              />
              <span>⚡ Upload immediately on select / drop</span>
            </label>
            <span style={{ fontSize: "12px", color: "var(--color-text-muted)", marginLeft: "24px" }}>
              {autoUpload ? "Files ingest directly as soon as dropped" : "Stage files below before uploading"}
            </span>
          </div>
        </div>

        {/* Dropzone with wide accept types */}
        <UploadZone
          onFiles={onFiles}
          accept=".pdf,.doc,.docx,.ppt,.pptx,.txt,.md,.csv,.rtf,.html,.htm,.json,.xml"
        />

        {/* Supported badges banner */}
        <div
          style={{
            marginTop: "var(--space-3)",
            display: "flex",
            flexWrap: "wrap",
            gap: "6px",
            alignItems: "center",
            fontSize: "12px",
            color: "var(--color-text-muted)",
          }}
        >
          <span style={{ fontWeight: 600, color: "var(--color-text-secondary)" }}>Supported Formats:</span>
          {["PDF", "DOC", "DOCX", "PPT", "PPTX", "TXT", "MD", "CSV", "RTF", "HTML", "JSON", "XML"].map((fmt) => (
            <span
              key={fmt}
              style={{
                background: "rgba(255, 255, 255, 0.08)",
                border: "1px solid rgba(56, 189, 248, 0.25)",
                color: "var(--color-text)",
                padding: "2px 8px",
                borderRadius: "4px",
                fontFamily: "monospace",
                fontSize: "12px",
                fontWeight: 500,
              }}
            >
              .{fmt.toLowerCase()}
            </span>
          ))}
        </div>

        {/* Pending Review Queue (when autoUpload is false or files staged) */}
        {pending.length > 0 && (
          <div style={{ marginTop: "var(--space-5)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "var(--space-3)" }}>
              <h2 style={{ fontSize: "var(--font-size-sm)", fontWeight: 600, margin: 0 }}>
                Staged for Ingestion ({pending.length} file{pending.length > 1 ? "s" : ""})
              </h2>
              <div style={{ display: "flex", gap: "8px" }}>
                <Button variant="ghost" size="sm" onClick={() => setPending([])} disabled={!!busyFile}>
                  Discard All
                </Button>
                <Button variant="primary" size="sm" onClick={uploadAll} loading={!!busyFile}>
                  {busyFile ? `Uploading ${busyFile}...` : `Upload All (${pending.length})`}
                </Button>
              </div>
            </div>

            <div className="ui-table-wrap" style={{ marginBottom: "var(--space-4)" }}>
              <table className="ui-table ui-table--compact">
                <thead>
                  <tr>
                    <th>File</th>
                    <th>Size</th>
                    <th>Classification</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {pending.map((f, i) => (
                    <tr key={`${f.name}-${f.lastModified}-${i}`}>
                      <td className="ui-table__title">
                        <span style={{ fontWeight: 600 }}>{f.name}</span>
                      </td>
                      <td className="ui-muted">{fmtSize(f.size)}</td>
                      <td>
                        <ClassificationBadge classification={classification} />
                      </td>
                      <td>
                        <div className="ui-table__actions" style={{ gap: "6px" }}>
                          <Button
                            variant="primary"
                            size="sm"
                            onClick={() => uploadSingle(f)}
                            loading={busyFile === f.name}
                            disabled={!!busyFile && busyFile !== f.name}
                          >
                            Upload Now
                          </Button>
                          <Tooltip content="Remove from staging">
                            <DeleteButton
                              size="sm"
                              onClick={() => removePending(f)}
                              disabled={busyFile === f.name}
                              title="Remove file"
                              aria-label={`Remove ${f.name}`}
                            />
                          </Tooltip>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </Card>

      {/* Ingestion Status Pipeline Card */}
      <Card
        title="Live Ingestion Pipeline"
        subtitle="Real-time multi-stage parser, chunker, and vector embedding status"
      >
        {uploads.length === 0 ? (
          <p className="ui-muted" style={{ margin: 0, fontSize: "var(--font-size-sm)", padding: "var(--space-2) 0" }}>
            No uploads in this active session. Upload documents above to view live parsing, OCR, and embedding pipelines.
          </p>
        ) : (
          <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-4)" }}>
            {uploads.map((u) => (
              <div
                key={u.jobId || u.documentId || u.name}
                style={{
                  padding: "var(--space-3)",
                  background: "rgba(255, 255, 255, 0.02)",
                  borderRadius: "var(--radius-md)",
                  border: "1px solid var(--color-border)",
                }}
              >
                <div className="ui-row ui-row--between" style={{ alignItems: "center", marginBottom: "var(--space-2)" }}>
                  <div style={{ display: "flex", alignItems: "center", gap: "10px", minWidth: 0, flexWrap: "wrap" }}>
                    <strong style={{ fontSize: "var(--font-size-sm)" }}>{u.name}</strong>
                    {u.size && <span className="ui-small ui-muted">{fmtSize(u.size)}</span>}
                    {u.duplicate && (
                      <span
                        style={{
                          fontSize: "11px",
                          padding: "1px 6px",
                          borderRadius: "4px",
                          background: "rgba(59, 130, 246, 0.15)",
                          color: "#93c5fd",
                          border: "1px solid rgba(59, 130, 246, 0.3)",
                        }}
                      >
                        Duplicate (Already indexed)
                      </span>
                    )}
                    {u.time && <span className="ui-small ui-muted">{u.time}</span>}
                  </div>
                  <div className="ui-row" style={{ gap: "var(--space-2)" }}>
                    {u.status === "FAILED" && u.jobId && (
                      <Button variant="secondary" size="sm" onClick={() => retry(u.jobId)}>
                        Retry
                      </Button>
                    )}
                    {u.documentId && (
                      <Link className="ui-btn ui-btn--secondary ui-btn--sm" to={`/documents/${u.documentId}`}>
                        Open Details
                      </Link>
                    )}
                    {u.documentId && u.status === "READY" && (
                      <Link className="ui-btn ui-btn--primary ui-btn--sm" to={`/assistant?docId=${u.documentId}`}>
                        Ask AI
                      </Link>
                    )}
                  </div>
                </div>
                <IngestionStepper status={u.status} errorMessage={u.error} />
              </div>
            ))}
          </div>
        )}
      </Card>

      {/* Existing Vault Documents Card */}
      <Card
        title="Recently Indexed Knowledge"
        subtitle="Documents currently authorized and available for grounded semantic retrieval"
      >
        {loadingDocs ? (
          <p className="ui-muted" style={{ margin: 0, fontSize: "var(--font-size-sm)" }}>Loading knowledge vault...</p>
        ) : recentDocs.length === 0 ? (
          <p className="ui-muted" style={{ margin: 0, fontSize: "var(--font-size-sm)" }}>
            No documents found in your authorized vault yet. Upload documents above to begin.
          </p>
        ) : (
          <div className="ui-table-wrap">
            <table className="ui-table ui-table--compact">
              <thead>
                <tr>
                  <th>Title</th>
                  <th>Classification</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {recentDocs.map((d) => (
                  <tr key={d.id}>
                    <td className="ui-table__title">
                      <Link to={`/documents/${d.id}`} style={{ color: "var(--color-text)", fontWeight: 600 }}>
                        {d.title || d.sourceFilename}
                      </Link>
                    </td>
                    <td>
                      <ClassificationBadge classification={d.classification} />
                    </td>
                    <td>
                      <StatusBadge status="READY" />
                    </td>
                    <td>
                      <div className="ui-table__actions" style={{ gap: "6px" }}>
                        <Link className="ui-btn ui-btn--ghost ui-btn--sm" to={`/documents/${d.id}/view`}>
                          View
                        </Link>
                        <Link className="ui-btn ui-btn--secondary ui-btn--sm" to={`/assistant?docId=${d.id}`}>
                          Query
                        </Link>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <div style={{ marginTop: "var(--space-3)", textAlign: "right" }}>
          <Link className="ui-btn ui-btn--ghost ui-btn--sm" to="/documents">
            View All Documents →
          </Link>
        </div>
      </Card>
    </div>
  );
}