import { useEffect, useState, useCallback } from "react";
import { Link } from "react-router-dom";
import { documentsApi } from "../api";
import {
  Card, ClassificationBadge, StatusBadge, ErrorState, EmptyState,
  Skeleton, Pagination, Button, DeleteButton, ConfirmDialog, useToasts, ToastContainer, Tooltip,
} from "../components/UI";
import { useAuth } from "../context/AuthContext";
import { useProfile } from "../context/ProfileContext";

const PAGE_SIZE = 20;

export default function Documents() {
  const { auth } = useAuth();
  const { getDisplayName } = useProfile();
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [confirm, setConfirm] = useState(null);
  const [deletingId, setDeletingId] = useState(null);
  const { toasts, dismiss, success, error: fail } = useToasts();

  const load = useCallback((p) => {
    setData(null);
    setError(null);
    documentsApi.list(p, PAGE_SIZE).then(setData).catch((e) => setError(e.message));
  }, []);

  useEffect(() => { load(page); }, [page, load]);

  const handleDelete = async () => {
    if (!confirm) return;
    const id = confirm.id;
    setDeletingId(id);
    try {
      await documentsApi.delete(id);
      setData((prev) => (prev ? { ...prev, content: prev.content.filter((d) => d.id !== id), totalElements: (prev.totalElements || 1) - 1 } : prev));
      success(`"${confirm.name}" and all versions were deleted.`, "Document deleted");
      setConfirm(null);
    } catch (e) {
      fail(e.message, "Delete failed");
    } finally {
      setDeletingId(null);
    }
  };

  const items = data?.content || [];

  return (
    <div className="ui-stack">
      <ToastContainer toasts={toasts} onDismiss={dismiss} />

      <header className="ui-page-head">
        <div>
          <h1 className="ui-page-head__title">Documents</h1>
          <p className="ui-page-head__sub">Authorized documents in your knowledge workspace.</p>
        </div>
        <div className="ui-row" style={{ gap: "var(--space-2)" }}>
          <Button variant="secondary" size="sm" onClick={() => load(page)} disabled={!data}>Refresh</Button>
          <Link className="ui-btn ui-btn--primary ui-btn--sm" to="/knowledge">Upload document</Link>
        </div>
      </header>

      <Card padding="none" elevated>
        {error && <div style={{ padding: "var(--space-5)" }}><ErrorState message={error} onRetry={() => load(page)} /></div>}

        {!data && !error && (
          <div style={{ padding: "var(--space-5)" }} className="ui-stack ui-stack--sm">
            {[0, 1, 2, 3, 4].map((i) => <Skeleton key={i} variant="row" />)}
          </div>
        )}

        {data && items.length === 0 && (
          <div style={{ padding: "var(--space-5)" }}>
            <EmptyState
              title="No authorized documents"
              action={<Link className="ui-btn ui-btn--primary ui-btn--sm" to="/knowledge">Upload a document</Link>}
            >
              Nothing is available at your current clearance, or the workspace is empty.
            </EmptyState>
          </div>
        )}

        {data && items.length > 0 && (
          <>
            <div className="ui-table-wrap">
              <table className="ui-table">
                <thead>
                  <tr>
                    <th>Title</th>
                    <th>Classification</th>
                    <th>Owner</th>
                    <th>Ver.</th>
                    <th>Status</th>
                    <th>Language</th>
                    <th>Updated</th>
                    <th style={{ textAlign: "right" }}>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map((d) => (
                    <tr key={d.id}>
                      <td>
                        <Link className="ui-table__title" to={`/documents/${d.id}`}>{d.title || d.sourceFilename}</Link>
                        <div className="ui-table__meta ui-truncate" style={{ maxWidth: 300, color: "#94a3b8", fontSize: "12px", marginTop: "2px" }}>
                          {d.sourceFilename}
                        </div>
                      </td>
                      <td><ClassificationBadge value={d.classification} size="sm" /></td>
                      <td className="ui-small" style={{ color: "#cbd5e1" }}>
                        {d.ownerId === auth?.userId ? (
                          <span style={{ color: "#38bdf8", fontWeight: 600 }}>{getDisplayName()} (You)</span>
                        ) : d.ownerId ? (
                          <span>Officer ({d.ownerId.slice(0, 8)})</span>
                        ) : "—"}
                      </td>
                      <td className="ui-mono" style={{ color: "#cbd5e1" }}>{d.version ?? "—"}</td>
                      <td><StatusBadge status={d.status} size="sm" /></td>
                      <td className="ui-small" style={{ color: "#94a3b8" }}>{d.language || "—"}</td>
                      <td className="ui-small" style={{ color: "#94a3b8" }}>{d.createdAt ? new Date(d.createdAt).toLocaleDateString() : "—"}</td>
                      <td>
                        <div className="ui-table__actions">
                          <Tooltip content="Open document">
                            <Link className="ui-btn ui-btn--secondary ui-btn--sm" to={`/documents/${d.id}`}>Open</Link>
                          </Tooltip>
                          <Tooltip content="Delete permanently">
                            <DeleteButton
                              size="sm"
                              onClick={() => setConfirm({ id: d.id, name: d.title || d.sourceFilename })}
                              disabled={deletingId === d.id}
                              aria-label={`Delete ${d.title || d.sourceFilename}`}
                              title="Delete permanently"
                            />
                          </Tooltip>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div style={{ padding: "0 var(--space-5) var(--space-4)" }}>
              <Pagination page={page} totalPages={data.totalPages} onChange={setPage} />
            </div>
          </>
        )}
      </Card>

      <ConfirmDialog
        isOpen={!!confirm}
        title="Delete this document?"
        message={`"${confirm?.name}" and all of its versions will be permanently removed. This cannot be undone and the action is audited.`}
        confirmLabel="Delete permanently"
        loading={deletingId === confirm?.id}
        onConfirm={handleDelete}
        onCancel={() => setConfirm(null)}
      />
    </div>
  );
}