import { NavLink, useLocation, useNavigate } from "react-router-dom";
import { useEffect, useState } from "react";
import { useAuth } from "../context/AuthContext";
import { useProfile } from "../context/ProfileContext";
import { t } from "../i18n";
import { Button } from "../components/UI";
import CyberPattern from "./CyberPattern";
import ProfileModal from "./ProfileModal";

const ic = {
  dashboard: <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.7} d="M3.75 6A2.25 2.25 0 016 3.75h2.25A2.25 2.25 0 0110.5 6v2.25a2.25 2.25 0 01-2.25 2.25H6a2.25 2.25 0 01-2.25-2.25V6zM3.75 15.75A2.25 2.25 0 016 13.5h2.25a2.25 2.25 0 012.25 2.25V18a2.25 2.25 0 01-2.25 2.25H6A2.25 2.25 0 013.75 18v-2.25zM13.5 6a2.25 2.25 0 012.25-2.25H18A2.25 2.25 0 0120.25 6v2.25A2.25 2.25 0 0118 10.5h-2.25a2.25 2.25 0 01-2.25-2.25V6zM13.5 15.75a2.25 2.25 0 012.25-2.25H18a2.25 2.25 0 012.25 2.25V18A2.25 2.25 0 0118 20.25h-2.25A2.25 2.25 0 0113.5 18v-2.25z" /></svg>,
  assistant: <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.7} d="M8.625 12a.375.375 0 11-.75 0 .375.375 0 01.75 0zm3.75 0a.375.375 0 11-.75 0 .375.375 0 01.75 0zm3.75 0a.375.375 0 11-.75 0 .375.375 0 01.75 0zM21 12c0 4.556-4.03 8.25-9 8.25a9.764 9.764 0 01-2.555-.337A5.972 5.972 0 015.41 20.97a5.969 5.969 0 01-.474-.065 4.48 4.48 0 00.978-2.025c.09-.457-.133-.901-.467-1.226C3.93 16.178 3 14.189 3 12c0-4.556 4.03-8.25 9-8.25s9 3.694 9 8.25z" /></svg>,
  knowledge: <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.7} d="M20.25 7.5l-.625 10.632a2.25 2.25 0 01-2.247 2.118H6.622a2.25 2.25 0 01-2.247-2.118L3.75 7.5m8.25 3v6.75m0 0l-3-3m3 3l3-3M3.375 7.5h17.25c.621 0 1.125-.504 1.125-1.125v-1.5c0-.621-.504-1.125-1.125-1.125H3.375c-.621 0-1.125.504-1.125 1.125v1.5c0 .621.504 1.125 1.125 1.125z" /></svg>,
  documents: <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.7} d="M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25m2.25 0H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 00-9-9z" /></svg>,
  studio: <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.7} d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09zM18.259 8.715L18 9.75l-.259-1.035a3.375 3.375 0 00-2.455-2.456L14.25 6l1.036-.259a3.375 3.375 0 002.455-2.456L18 2.25l.259 1.035a3.375 3.375 0 002.456 2.456L21.75 6l-1.035.259a3.375 3.375 0 00-2.456 2.456z" /></svg>,
  search: <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.7} d="M21 21l-5.197-5.197m0 0A7.5 7.5 0 105.196 5.196a7.5 7.5 0 0010.607 10.607z" /></svg>,
  generated: <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.7} d="M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25m0 12.75h7.5m-7.5 3H12M10.5 2.25H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 00-9-9z" /></svg>,
  audit: <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.7} d="M9 12h3.75M9 15h3.75M9 18h3.75m3 .75H18a2.25 2.25 0 002.25-2.25V6.108c0-1.135-.845-2.098-1.976-2.192a48.424 48.424 0 00-1.123-.08m-5.801 0c-.065.21-.1.433-.1.664 0 .414.336.75.75.75h4.5a.75.75 0 00.75-.75 2.25 2.25 0 00-.1-.664m-5.8 0A2.251 2.251 0 0113.5 2.25H15c1.012 0 1.867.668 2.15 1.586m-5.8 0c-.376.023-.75.05-1.124.08C9.095 4.01 8.25 4.973 8.25 6.108V8.25m0 0H4.875c-.621 0-1.125.504-1.125 1.125v11.25c0 .621.504 1.125 1.125 1.125h9.75c.621 0 1.125-.504 1.125-1.125V9.375c0-.621-.504-1.125-1.125-1.125H8.25zM6.75 12h.008v.008H6.75V12zm0 3h.008v.008H6.75V15zm0 3h.008v.008H6.75V18z" /></svg>,
  security: <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.7} d="M16.5 10.5V6.75a4.5 4.5 0 10-9 0v3.75m-.75 11.25h10.5a2.25 2.25 0 002.25-2.25v-6.75a2.25 2.25 0 00-2.25-2.25H6.75a2.25 2.25 0 00-2.25 2.25v6.75a2.25 2.25 0 002.25 2.25z" /></svg>,
  settings: <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.7} d="M9.594 3.94c.09-.542.56-.94 1.11-.94h2.593c.55 0 1.02.398 1.11.94l.213 1.281c.063.374.313.686.645.87.074.04.147.083.22.127.324.196.72.257 1.075.124l1.217-.456a1.125 1.125 0 011.37.49l1.296 2.247a1.125 1.125 0 01-.26 1.431l-1.003.827c-.293.241-.438.613-.43.992a7.723 7.723 0 010 .255c-.008.378.137.75.43.991l1.004.827c.424.35.534.955.26 1.43l-1.298 2.247a1.125 1.125 0 01-1.369.491l-1.217-.456c-.355-.133-.75-.072-1.076.124a6.47 6.47 0 01-.22.128c-.331.183-.581.495-.644.869l-.213 1.28c-.09.543-.56.941-1.11.941h-2.594c-.55 0-1.019-.398-1.11-.94l-.213-1.281c-.062-.374-.312-.686-.644-.87a6.52 6.52 0 01-.22-.127c-.325-.196-.72-.257-1.076-.124l-1.217.456a1.125 1.125 0 01-1.369-.49l-1.297-2.247a1.125 1.125 0 01.26-1.431l1.004-.827c.292-.24.437-.613.43-.991a6.932 6.932 0 010-.255c.007-.38-.138-.751-.43-.992l-1.004-.827a1.125 1.125 0 01-.26-1.43l1.297-2.247a1.125 1.125 0 011.37-.491l1.216.456c.356.133.751.072 1.076-.124.072-.044.146-.086.22-.128.332-.183.582-.495.644-.869l.214-1.28zM15 12a3 3 0 11-6 0 3 3 0 016 0z" /></svg>,
};

const NAV_SECTIONS = [
  {
    title: "Workspace",
    items: [
      { to: "/dashboard", key: "dashboard", label: () => t("nav.dashboard", "Dashboard") },
      { to: "/assistant", key: "assistant", label: () => t("nav.assistant", "AI Assistant") },
      { to: "/search", key: "search", label: () => t("nav.search", "Search") },
    ],
  },
  {
    title: "Knowledge",
    items: [
      { to: "/knowledge", key: "knowledge", label: () => t("nav.knowledge", "Knowledge Vault") },
      { to: "/documents", key: "documents", label: () => t("nav.documents", "Documents") },
      { to: "/studio", key: "studio", label: () => t("nav.studio", "Document Studio") },
      { to: "/generated", key: "generated", label: () => t("nav.generated", "Generated Files") },
    ],
  },
  {
    title: "Governance",
    items: [
      { to: "/audit", key: "audit", label: () => t("nav.audit", "Audit & Activity"), roles: ["ADMIN", "SECURITY_ADMIN"], badge: "ADMIN" },
      { to: "/security", key: "security", label: () => t("nav.security", "Security & Privacy") },
      { to: "/settings", key: "settings", label: () => t("nav.settings", "Settings") },
    ],
  },
];

const SHIELD = (
  <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M9 12.75L11.25 15 15 9.75M21 12c0 4.97-4.03 9-9 9s-9-4.03-9-9 4.03-9 9-9c1.19 0 2.33.22 3.36.62M21 12a9 9 0 00-9-9" />
  </svg>
);

const BOUNDARY = [
  "Documents remain within your configured infrastructure.",
  "Retrieval is permission-filtered on the backend.",
  "External model transfer is policy controlled.",
  "AI responses are grounded in authorized sources.",
];

export default function Layout({ children }) {
  const { auth, logout } = useAuth();
  const { profile, getDisplayName, getInitials, getAvatarStyle, openProfile } = useProfile();
  const navigate = useNavigate();
  const location = useLocation();
  const [secOpen, setSecOpen] = useState(false);
  const [navOpen, setNavOpen] = useState(false);
  const [, force] = useState(0);

  useEffect(() => {
    const h = () => force((n) => n + 1);
    window.addEventListener("sachet:language", h);
    return () => window.removeEventListener("sachet:language", h);
  }, []);

  // Close the mobile drawer whenever the route changes.
  useEffect(() => { setNavOpen(false); }, [location.pathname]);

  const allItems = NAV_SECTIONS.flatMap((s) => s.items);
  const active = allItems.find((n) => n.to === location.pathname);
  const title = active
    ? active.label()
    : location.pathname.startsWith("/documents/")
      ? "Document"
      : t("appName", "SACHET AI");

  const roles = auth?.roles || [];

  return (
    <div className="app-shell">
      <CyberPattern fixed />
      <ProfileModal />
      <a className="ui-skip-link" href="#main-content">Skip to main content</a>

      {navOpen && <div className="sidebar-backdrop" onClick={() => setNavOpen(false)} aria-hidden="true" />}

      <nav className={`sidebar ${navOpen ? "sidebar--open" : ""}`} aria-label="Primary">
        <div className="sidebar__brand">
          <span className="sidebar__logo" aria-hidden="true">{SHIELD}</span>
          <span>
            <span className="sidebar__name">SACHET AI</span>
            <span className="sidebar__tagline">Security-first Knowledge Intelligence</span>
          </span>
        </div>

        <div style={{ marginTop: "var(--space-4)", display: "flex", flexDirection: "column", gap: "var(--space-4)" }}>
          {NAV_SECTIONS.map((sec) => {
            const filteredItems = sec.items.filter(
              (item) => !item.roles || item.roles.some((r) => roles.includes(r))
            );
            if (!filteredItems.length) return null;

            return (
              <div className="nav-section" key={sec.title}>
                <p className="nav-section__label">{sec.title}</p>
                <ul className="nav-list">
                  {filteredItems.map((item) => (
                    <li key={item.to}>
                      <NavLink to={item.to} className={({ isActive }) => (isActive ? "active" : "")}>
                        {ic[item.key]}
                        <span>{item.label()}</span>
                        {item.badge && <span className="nav-badge">{item.badge}</span>}
                      </NavLink>
                    </li>
                  ))}
                </ul>
              </div>
            );
          })}
        </div>

        {/* Sidebar User Profile Card & Controls */}
        <div className="sidebar__user" style={{ padding: "16px 12px", borderTop: "1px solid rgba(56, 189, 248, 0.22)" }}>
          <div
            onClick={openProfile}
            style={{
              display: "flex",
              alignItems: "center",
              gap: "10px",
              padding: "8px 10px",
              borderRadius: "10px",
              background: "rgba(255, 255, 255, 0.03)",
              border: "1px solid rgba(56, 189, 248, 0.15)",
              cursor: "pointer",
              transition: "all 0.15s ease",
              marginBottom: "10px",
            }}
            onMouseOver={(e) => {
              e.currentTarget.style.background = "rgba(56, 189, 248, 0.08)";
              e.currentTarget.style.borderColor = "rgba(56, 189, 248, 0.35)";
            }}
            onMouseOut={(e) => {
              e.currentTarget.style.background = "rgba(255, 255, 255, 0.03)";
              e.currentTarget.style.borderColor = "rgba(56, 189, 248, 0.15)";
            }}
            title="Click to view & customize your profile"
          >
            <div
              style={{
                width: "36px",
                height: "36px",
                borderRadius: "50%",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                fontWeight: 700,
                fontSize: "13px",
                flexShrink: 0,
                ...getAvatarStyle(),
              }}
            >
              {getInitials()}
            </div>
            <div style={{ minWidth: 0, flex: 1 }}>
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: "4px" }}>
                <span
                  style={{
                    fontSize: "13px",
                    fontWeight: 600,
                    color: "#ffffff",
                    overflow: "hidden",
                    textOverflow: "ellipsis",
                    whiteSpace: "nowrap",
                    display: "block",
                  }}
                >
                  {getDisplayName()}
                </span>
                <span
                  style={{
                    fontSize: "10px",
                    padding: "1px 5px",
                    borderRadius: "4px",
                    background: "rgba(74, 222, 128, 0.15)",
                    color: "#86efac",
                    border: "1px solid rgba(74, 222, 128, 0.3)",
                    fontWeight: 600,
                  }}
                >
                  {profile.clearance || "INTERNAL"}
                </span>
              </div>
              <span
                style={{
                  fontSize: "11px",
                  color: "#94a3b8",
                  overflow: "hidden",
                  textOverflow: "ellipsis",
                  whiteSpace: "nowrap",
                  display: "block",
                  marginTop: "2px",
                }}
              >
                {profile.title || "Intelligence Analyst"}
              </span>
            </div>
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "6px" }}>
            <Button
              variant="secondary"
              size="sm"
              onClick={openProfile}
              style={{ width: "100%", justifyContent: "center", fontSize: "12px", padding: "6px 8px" }}
            >
              <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" width="13" height="13" aria-hidden="true">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M15.75 6a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0zM4.501 20.118a7.5 7.5 0 0114.998 0A17.933 17.933 0 0112 21.75c-2.676 0-5.216-.584-7.499-1.632z" />
              </svg>
              <span>Profile</span>
            </Button>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => { logout(); navigate("/login", { replace: true }); }}
              style={{ width: "100%", justifyContent: "center", fontSize: "12px", padding: "6px 8px" }}
              aria-label="Sign out"
            >
              <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" width="13" height="13" aria-hidden="true">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M15.75 9V5.25A2.25 2.25 0 0013.5 3h-6a2.25 2.25 0 00-2.25 2.25v13.5A2.25 2.25 0 007.5 21h6a2.25 2.25 0 002.25-2.25V15M12 9l-3 3m0 0l3 3m-3-3h12.75" />
              </svg>
              <span>Sign out</span>
            </Button>
          </div>
        </div>
      </nav>

      <div className="main-col">
        <header className="app-topbar">
          <button
            className="icon-toggle ui-hide-tablet"
            onClick={() => setNavOpen(true)}
            aria-label="Open navigation menu"
            aria-expanded={navOpen}
          >
            <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M3.75 6.75h16.5M3.75 12h16.5m-16.5 5.25h16.5" />
            </svg>
          </button>

          <h1 className="app-topbar__title">{title}</h1>

          <div className="app-search ui-hide-mobile">
            <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M21 21l-5.197-5.197m0 0A7.5 7.5 0 105.196 5.196a7.5 7.5 0 0010.607 10.607z" />
            </svg>
            <input
              type="search"
              placeholder="Search authorized documents..."
              aria-label="Search authorized documents"
              onKeyDown={(e) => {
                if (e.key === "Enter" && e.currentTarget.value.trim()) {
                  navigate(`/search?q=${encodeURIComponent(e.currentTarget.value.trim())}`);
                }
              }}
            />
          </div>

          <div style={{ marginLeft: "auto", display: "flex", alignItems: "center", gap: "10px" }}>
            <button
              type="button"
              onClick={openProfile}
              style={{
                display: "inline-flex",
                alignItems: "center",
                gap: "8px",
                padding: "4px 10px 4px 6px",
                borderRadius: "20px",
                background: "rgba(255, 255, 255, 0.04)",
                border: "1px solid rgba(56, 189, 248, 0.2)",
                color: "#f8fafc",
                fontSize: "12px",
                fontWeight: 600,
                cursor: "pointer",
                transition: "all 0.15s ease",
              }}
              onMouseOver={(e) => {
                e.currentTarget.style.borderColor = "#38bdf8";
                e.currentTarget.style.background = "rgba(56, 189, 248, 0.08)";
              }}
              onMouseOut={(e) => {
                e.currentTarget.style.borderColor = "rgba(56, 189, 248, 0.2)";
                e.currentTarget.style.background = "rgba(255, 255, 255, 0.04)";
              }}
              title="Open Profile Settings"
            >
              <span
                style={{
                  width: "22px",
                  height: "22px",
                  borderRadius: "50%",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  fontSize: "10px",
                  fontWeight: 700,
                  ...getAvatarStyle(),
                }}
              >
                {getInitials()}
              </span>
              <span>{getDisplayName()}</span>
            </button>

            <button
              className="security-pill"
              onClick={() => setSecOpen((v) => !v)}
              aria-expanded={secOpen}
              aria-controls="security-boundary"
            >
              {SHIELD}
              <span>Secure Processing</span>
            </button>
          </div>
        </header>

        {secOpen && (
          <div className="security-drawer" id="security-boundary">
            <p style={{ margin: "0 0 var(--space-3)", fontSize: "var(--font-size-xs)", fontWeight: 700, letterSpacing: "0.06em", textTransform: "uppercase", color: "var(--color-success-400)" }}>
              Security boundary
            </p>
            <ul>
              {BOUNDARY.map((line) => (
                <li key={line}>
                  <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.2} d="M4.5 12.75l6 6 9-13.5" />
                  </svg>
                  <span>{line}</span>
                </li>
              ))}
            </ul>
          </div>
        )}

        <main className="app-content" id="main-content">{children}</main>
      </div>
    </div>
  );
}