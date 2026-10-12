import { useEffect, useState, useCallback } from "react";
import { auditApi } from "../api/phase5";
import { Card, ErrorState, EmptyState, Skeleton, Pagination, Badge, Alert } from "../components/UI";

const PAGE_SIZE = 20;

const ACTION_TONE = (action = "") => {
  const a = action.toUpperCase();
  if (a.includes("DELETE") || a.includes("DENY")) return "danger";
  if (a.includes("EXPORT") || a.includes("DOWNLOAD") || a.includes("SHARE")) return "warning";
  if (a.includes("LOGIN") || a.includes("AUTH")) return "info";
  if (a.includes("CREATE") || a.includes("UPLOAD")) return "success";
  return "default";
};

export default function Audit() {
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);

  const load = useCallback((p) => {
    setData(null);
    setError(null);
    auditApi.list(p, PAGE_SIZE).then(setData).catch((e) => setError(e.message));
  }, []);

  useEffect(() => { load(page); }, [page, load]);

  const items = data?.content || [];

  return (
    <div className="ui-stack">
      <header className="ui-page-head">
        <div>
          <h1 className="ui-page-head__title">Audit &amp; activity</h1>
          <p className="ui-page-head__sub">Backend-controlled audit trail. Reading this page requires an administrator role.</p>
        </div>
      </header>

      <Card padding="none" elevated>
        {error && (
          <div style={{ padding: "var(--space-5)" }}>
            {error.status === 403
              ? <Alert kind="warning" title="Access denied">Your account is not authorized to read the audit log. This is enforced server-side, not in the interface.</Alert>
              : <ErrorState message={error} onRetry={() => load(page)} />}
          </div>
        )}

        {!data && !error && (
          <div style={{ padding: "var(--space-5)" }} className="ui-stack ui-stack--sm">
            {[0, 1, 2, 3, 4].map((i) => <Skeleton key={i} variant="row" />)}
          </div>
        )}

        {data && items.length === 0 && (
          <div style={{ padding: "var(--space-5)" }}>
            <EmptyState title="No activity to display">
              No audit events match the current authorization scope.
            </EmptyState>
          </div>
        )}

        {data && items.length > 0 && (
          <>
            <div className="ui-table-wrap">
              <table className="ui-table">
                <thead>
                  <tr>
                    <th>Timestamp</th><th>Actor</th><th>Action</th><th>Resource</th>
                    <th>Classification</th><th>Model</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map((e) => (
                    <tr key={e.id}>
                      <td className="ui-small ui-muted" style={{ whiteSpace: "nowrap" }}>
                        {e.createdAt ? new Date(e.createdAt).toLocaleString() : "—"}
                      </td>
                      <td className="ui-table__title">{e.actor || "—"}</td>
                      <td><span className={`audit-action ui-badge--${ACTION_TONE(e.action)}`}>{e.action || "—"}</span></td>
                      <td className="ui-small ui-muted">{e.docIds || "—"}</td>
                      <td>{e.classification ? <Badge size="sm" variant="default" dot>{e.classification}</Badge> : <span className="ui-muted">—</span>}</td>
                      <td className="ui-small ui-muted">{e.model || "—"}</td>
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
    </div>
  );
}