import React from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import App from "./App.jsx";
import { AuthProvider } from "./context/AuthContext.jsx";
import "./styles/global.css";

createRoot(document.getElementById("root")).render(
  <React.StrictMode>
    {/* Opt in to the v7 behaviours now so the upgrade is a version bump, not a
        behavioural change. Both flags are the documented forward-compatible
        options React Router v6 emits deprecation warnings for. */}
    <BrowserRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </BrowserRouter>
  </React.StrictMode>
);
