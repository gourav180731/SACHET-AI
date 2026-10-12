import { useState, useEffect } from "react";
import { Card, Select, DefList, Badge, Alert, Toggle } from "../components/UI";
import { useAuth } from "../context/AuthContext";
import { SUPPORTED_LANGUAGES, setLanguage, getLanguage } from "../i18n";

const STT_NOTE = "Browser Web Speech API";

export default function Settings() {
  const { auth } = useAuth();
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
    <div className="ui-stack">
      <header className="ui-page-head">
        <div>
          <h1 className="ui-page-head__title">Settings</h1>
          <p className="ui-page-head__sub">Interface preferences for your account on this deployment.</p>
        </div>
      </header>

      <div className="ui-grid ui-grid--2">
        <Card title="Account" subtitle="Identity resolved from your session token">
          <DefList items={[
            ["User", <strong>{auth?.userId}</strong>],
            ["Roles", (auth?.roles || []).length
              ? <span className="ui-row" style={{ gap: 6 }}>
                  {(auth?.roles || []).map((r) => <Badge key={r} variant="primary" size="sm">{r}</Badge>)}
                </span>
              : <Badge size="sm">USER</Badge>],
            ["Session", "JWT Bearer — expiry is controlled by backend configuration."],
          ]} />
        </Card>

        <Card title="Interface language" subtitle="Applies immediately across the app">
          <Select
            label="Language"
            value={lang}
            onChange={(e) => applyLanguage(e.target.value)}
            options={SUPPORTED_LANGUAGES.map((l) => ({ value: l.code, label: l.name }))}
            hint="Only languages with a backend dictionary are fully localized; the rest fall back to English."
          />
        </Card>
      </div>

      <Card title="Language support" subtitle="Per-capability availability">
        <div className="ui-table-wrap">
          <table className="ui-table">
            <thead>
              <tr><th>Language</th><th>UI text</th><th>Speech-to-text</th><th>Text-to-speech</th><th>AI generation</th></tr>
            </thead>
            <tbody>
              {SUPPORTED_LANGUAGES.map((l) => (
                <tr key={l.code}>
                  <td className="ui-table__title">{l.name}</td>
                  <td>{l.code === "en" ? <Badge variant="success" size="sm">Available</Badge> : <span className="ui-small ui-muted">Falls back to English</span>}</td>
                  <td className="ui-small ui-muted">{l.code === "en" ? STT_NOTE : "Provider dependent"}</td>
                  <td className="ui-small ui-muted">Browser speech synthesis</td>
                  <td className="ui-small ui-muted">Provider dependent</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      <Card title="Conversation language" subtitle="Set inside the AI Assistant">
        <Alert kind="info">
          The AI Assistant has its own language selector for conversation responses. It chooses the response
          language where the backend language provider supports it, independently of this interface setting.
        </Alert>
      </Card>

      <Card title="AI / model policy" subtitle="Enforced server-side">
        <Alert kind="warning" title="Not user-configurable">
          Model policy is enforced entirely by the backend. No provider configuration, credential or routing
          preference is exposed to ordinary users in this interface, by design.
        </Alert>
      </Card>

      <Card title="Client-side preferences" subtitle="Stored in this browser only">
        <Toggle
          checked
          disabled
          label="Reduce motion (respect system setting)"
          description="Animations already follow your OS prefers-reduced-motion setting and cannot be overridden to a higher-motion state."
        />
      </Card>
    </div>
  );
}