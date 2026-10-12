import { useEffect, useState } from "react";
import { healthApi } from "../api";
import { Card, Badge, Alert, Skeleton, DefList } from "../components/UI";

const FLOW = [
  ["Your browser", "AuthN"],
  ["Authentication", "AuthZ"],
  ["Authorization", "Policy"],
  ["Security policy", "Retrieval"],
  ["Permission-filtered retrieval", "Model"],
  ["Approved model gateway", "Output"],
  ["Grounded response", null],
];

const CONTROLS = [
  ["Authentication", "JWT Bearer tokens enforced on all protected endpoints."],
  ["Authorization", "Server-side RBAC plus document-level permission checks on every retrieval, export and tool call."],
  ["Document isolation", "Permission filtering runs before results reach the model. No global vector search bypasses your scope."],
  ["Sensitive mode", "External model transfers are disabled unless backend policy explicitly allows them."],
  ["External model policy", "NOT EXPOSED by a backend API — configuration is controlled server-side only."],
  ["Audit logging", "Events are recorded server-side without storing full sensitive content. The read API is role-gated."],
  ["Prompt injection defence", "System instructions, user input, document content and tool output are kept strictly separated."],
  ["Evidence handling", "Answers retain document, version, page and section provenance. Insufficient evidence is reported, never fabricated."],
];

const ArrowDown = () => (
  <span className="flow__arrow" aria-hidden="true">
    <svg fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19.5 12.75l-7.5 7.5-7.5-7.5M12 20.25V3.75" /></svg>
  </span>
);

export default function Security() {
  const [health, setHealth] = useState(null);
  const [ready, setReady] = useState(null);

  useEffect(() => {
    healthApi.health().then(setHealth).catch(() => setHealth({ error: true }));
    healthApi.ready().then(setReady).catch(() => setReady({ error: true }));
  }, []);

  const healthOk = health && !health.error;
  const readyOk = ready && !ready.error;

  return (
    <div className="ui-stack">
      <header className="ui-page-head">
        <div>
          <h1 className="ui-page-head__title">Security &amp; privacy</h1>
          <p className="ui-page-head__sub">Implemented controls and their current reported status.</p>
        </div>
      </header>

      <div className="ui-grid ui-grid--2">
        <Card title="Service status" subtitle="Live backend probe">
          {!health || !ready ? (
            <div className="ui-stack ui-stack--sm">
              <Skeleton variant="row" />
              <Skeleton variant="row" />
            </div>
          ) : (
            <DefList items={[
              ["Health", healthOk
                ? <Badge variant="success" dot>OK — {health.status}, version {health.version}</Badge>
                : <Badge variant="danger" dot>Unreachable</Badge>],
              ["Readiness", readyOk
                ? <Badge variant="success" dot>OK — {ready.status}</Badge>
                : <Badge variant="warning" dot>Not ready — a dependency is unavailable</Badge>],
            ]} />
          )}
          <div style={{ marginTop: "var(--space-4)" }}>
            <Alert kind="info">
              These values reflect what the backend reports right now. Absence of an error here is not a security certification.
            </Alert>
          </div>
        </Card>

        <Card title="Data processing boundary" subtitle="Configured request flow">
          <div className="flow">
            {FLOW.map(([label], i) => (
              <div key={label} style={{ display: "contents" }}>
                <div className="flow__step">
                  <span className="flow__num">{i + 1}</span>
                  <span>{label}</span>
                </div>
                {FLOW[i][1] && <ArrowDown />}
              </div>
            ))}
          </div>
          <p className="ui-small ui-muted" style={{ margin: "var(--space-4) 0 0" }}>
            This diagram describes the configured processing flow only. It is not a claim of any third-party certification.
          </p>
        </Card>
      </div>

      <Card title="Implemented controls" subtitle="What this deployment actually enforces">
        <DefList items={CONTROLS} />
      </Card>

      <Card title="Known limitations" subtitle="Documented, not hidden">
        <ul style={{ margin: 0, paddingLeft: 18, display: "flex", flexDirection: "column", gap: 8, fontSize: "var(--font-size-sm)", color: "var(--color-text-secondary)" }}>
          <li>External model policy and provider configuration are not exposed by any backend API in this build.</li>
          <li>The audit read API is available but restricted to administrator roles.</li>
          <li>Risk, recommendation and timeline extraction are NOT IMPLEMENTED in the current backend.</li>
          <li>Voice recognition depends on a browser API and is limited to Chromium-based browsers.</li>
          <li>No claim is made about resilience against a compromised backend or host.</li>
        </ul>
      </Card>
    </div>
  );
}