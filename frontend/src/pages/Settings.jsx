import { useState, useEffect } from "react";
import { Card, Select, DefList, Badge, Alert, Toggle, Button, ClassificationBadge } from "../components/UI";
import { useAuth } from "../context/AuthContext";
import { useProfile } from "../context/ProfileContext";
import { SUPPORTED_LANGUAGES, setLanguage, getLanguage } from "../i18n";

const STT_NOTE = "Browser Web Speech API";

export default function Settings() {
  const { auth } = useAuth();
  const { profile, getDisplayName, getInitials, getAvatarStyle, openProfile } = useProfile();
  const [lang, setLang] = useState(getLanguage());

  useEffect(() => {
    document.documentElement.lang = lang;
  }, [lang]);

  const applyLanguage = (code) => {
    setLang(code);
    setLanguage(code);
    window.dispatchEvent(new Event("sachet:language"));
  };

  return (
    <div className="ui-stack" style={{ gap: "var(--space-6)" }}>
      <header className="ui-page-head">
        <div>
          <h1 className="ui-page-head__title">Settings & Profile</h1>
          <p className="ui-page-head__sub">Manage your officer profile, identity preferences, and interface controls.</p>
        </div>
      </header>

      {/* User Profile Card */}
      <Card
        title="Personnel Profile & Customization"
        subtitle="Manage how your identity and rank appear across SACHET AI dashboards, audit trails, and document workspaces"
        actions={
          <Button variant="primary" size="sm" onClick={openProfile}>
            ✎ Edit Profile Details
          </Button>
        }
      >
        <div style={{ display: "flex", gap: "24px", alignItems: "flex-start", flexWrap: "wrap", padding: "8px 0" }}>
          {/* Avatar & Badge Display */}
          <div
            style={{
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              gap: "10px",
              padding: "16px 20px",
              borderRadius: "14px",
              background: "rgba(255, 255, 255, 0.03)",
              border: "1px solid rgba(56, 189, 248, 0.2)",
              minWidth: "160px",
            }}
          >
            <div
              style={{
                width: "64px",
                height: "64px",
                borderRadius: "50%",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                fontSize: "24px",
                fontWeight: 700,
                ...getAvatarStyle(),
              }}
            >
              {getInitials()}
            </div>
            <div style={{ textAlign: "center" }}>
              <strong style={{ fontSize: "14px", color: "#ffffff", display: "block" }}>{getDisplayName()}</strong>
              <span style={{ fontSize: "12px", color: "#94a3b8" }}>{profile.title || "Intelligence Analyst"}</span>
            </div>
            <ClassificationBadge classification={profile.clearance || "INTERNAL"} />
          </div>

          {/* Profile Details Grid */}
          <div style={{ flex: 1, minWidth: "260px" }}>
            <DefList
              items={[
                ["Full Name", <span style={{ color: "#ffffff", fontWeight: 600 }}>{profile.fullName || "—"}</span>],
                ["Call / Display Name", <span style={{ color: "#38bdf8", fontWeight: 600 }}>{profile.displayName || "—"}</span>],
                ["Designation / Rank", <span style={{ color: "#cbd5e1" }}>{profile.title || "—"}</span>],
                ["Department / Unit", <span style={{ color: "#cbd5e1" }}>{profile.department || "—"}</span>],
                ["Official Email", <span style={{ color: "#cbd5e1" }}>{profile.email || "—"}</span>],
                ["Direct Line / Ext.", <span style={{ color: "#cbd5e1" }}>{profile.phone || "—"}</span>],
                ["Mission Bio", <span style={{ color: "#94a3b8", fontStyle: "italic" }}>{profile.bio || "—"}</span>],
              ]}
            />
          </div>
        </div>
      </Card>

      <div className="ui-grid ui-grid--2">
        <Card title="Account & Session" subtitle="Verified identity resolved from your session token">
          <DefList
            items={[
              ["User ID", <code style={{ color: "#38bdf8", fontFamily: "monospace", fontSize: "12px" }}>{auth?.userId}</code>],
              [
                "RBAC Roles",
                (auth?.roles || []).length ? (
                  <span className="ui-row" style={{ gap: 6 }}>
                    {(auth?.roles || []).map((r) => (
                      <Badge key={r} variant="primary" size="sm">
                        {r}
                      </Badge>
                    ))}
                  </span>
                ) : (
                  <Badge size="sm">USER</Badge>
                ),
              ],
              ["Session Token", <span style={{ color: "#cbd5e1" }}>JWT Bearer (Server-side expiry enforced)</span>],
            ]}
          />
        </Card>

        <Card title="Interface Language" subtitle="Applies immediately across the app interface">
          <Select
            label="Language"
            value={lang}
            onChange={(e) => applyLanguage(e.target.value)}
            options={SUPPORTED_LANGUAGES.map((l) => ({ value: l.code, label: l.name }))}
            hint="Localized UI text is available for supported languages; others gracefully use English."
          />
        </Card>
      </div>

      <Card title="Multilingual Capabilities" subtitle="Per-language speech and model availability">
        <div className="ui-table-wrap">
          <table className="ui-table">
            <thead>
              <tr>
                <th>Language</th>
                <th>UI Text</th>
                <th>Speech-to-Text</th>
                <th>Text-to-Speech</th>
                <th>AI Generation</th>
              </tr>
            </thead>
            <tbody>
              {SUPPORTED_LANGUAGES.map((l) => (
                <tr key={l.code}>
                  <td className="ui-table__title">{l.name}</td>
                  <td>
                    {l.code === "en" ? (
                      <Badge variant="success" size="sm">
                        Available
                      </Badge>
                    ) : (
                      <span className="ui-small" style={{ color: "#94a3b8" }}>
                        Falls back to English
                      </span>
                    )}
                  </td>
                  <td className="ui-small" style={{ color: "#94a3b8" }}>
                    {l.code === "en" ? STT_NOTE : "Provider dependent"}
                  </td>
                  <td className="ui-small" style={{ color: "#94a3b8" }}>
                    Browser Web Speech
                  </td>
                  <td className="ui-small" style={{ color: "#94a3b8" }}>
                    Provider dependent
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      <Card title="AI / Model Governance" subtitle="Enforced server-side via ModelPolicy">
        <Alert kind="warning" title="Model Security Boundary">
          Model policies and gateway routing are enforced entirely by the backend security subsystem.
          No raw credentials or unverified model endpoints are exposed to ordinary clients.
        </Alert>
      </Card>

      <Card title="Accessibility & Motion" subtitle="Client-side display preferences">
        <Toggle
          checked
          disabled
          label="Reduce motion (respect system setting)"
          description="Interface animations automatically adhere to your operating system's prefers-reduced-motion setting for maximum comfort and stability."
        />
      </Card>
    </div>
  );
}