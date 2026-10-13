import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { useProfile } from "../context/ProfileContext";
import { Alert } from "../components/UI";
import CyberPattern from "../components/CyberPattern";

export default function Login() {
  const { login, loginWithGoogle } = useAuth();
  const { updateProfile } = useProfile();
  const navigate = useNavigate();

  const [email, setEmail] = useState("admin@sachet.gov");
  const [password, setPassword] = useState("Sachet@123");
  const [rememberMe, setRememberMe] = useState(true);
  const [isSignUp, setIsSignUp] = useState(false);
  const [fullName, setFullName] = useState("");
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);
  const [busy, setBusy] = useState(false);

  // Google SSO Modal & State
  const [showGoogleModal, setShowGoogleModal] = useState(false);
  const [googleEmail, setGoogleEmail] = useState("");
  const [googleName, setGoogleName] = useState("");
  const [googleBusy, setGoogleBusy] = useState(false);
  const [googleError, setGoogleError] = useState(null);

  // Initialize Google Identity Services if client ID is configured
  useEffect(() => {
    const googleClientId = import.meta.env.VITE_GOOGLE_CLIENT_ID;
    if (googleClientId && window.google?.accounts?.id) {
      try {
        window.google.accounts.id.initialize({
          client_id: googleClientId,
          callback: async (response) => {
            if (response?.credential) {
              try {
                setGoogleBusy(true);
                // Decode base64 JWT payload
                const base64Url = response.credential.split(".")[1];
                const base64 = base64Url.replace(/-/g, "+").replace(/_/g, "/");
                const payload = JSON.parse(decodeURIComponent(escape(window.atob(base64))));
                const gEmail = payload.email;
                const gName = payload.name || payload.given_name || "";
                const gPicture = payload.picture || "";

                await loginWithGoogle({
                  email: gEmail,
                  name: gName,
                  credential: response.credential,
                  picture: gPicture,
                });

                if (gName) {
                  updateProfile({ fullName: gName, displayName: gName.split(" ")[0], email: gEmail });
                }
                setShowGoogleModal(false);
                navigate("/dashboard", { replace: true });
              } catch (err) {
                setGoogleError(err.message || "Google authentication failed.");
              } finally {
                setGoogleBusy(false);
              }
            }
          },
        });
      } catch (err) {
        console.warn("Google One-Tap initialization notice:", err);
      }
    }
  }, [loginWithGoogle, navigate, updateProfile]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (busy) return;

    if (isSignUp) {
      setNotice(
        "Self-registration is restricted by government security policy. Clearance credentials are assigned by your organization's Security Officer (admin@sachet.gov)."
      );
      setError(null);
      return;
    }

    setBusy(true);
    setError(null);
    setNotice(null);

    try {
      await login(email, password);
      navigate("/dashboard", { replace: true });
    } catch (err) {
      setError(
        err.status === 0
          ? err.message
          : err.status === 401 || err.status === 403
          ? "Invalid credentials or unauthorized security clearance."
          : err.message || "Authentication failed. Please verify your clearance credentials."
      );
    } finally {
      setBusy(false);
    }
  };

  const handleForgotPassword = () => {
    setNotice(
      "To reset your security clearance passphrase, please contact the System Administrator at admin@sachet.gov or use demo credentials."
    );
    setError(null);
  };

  const handleOpenGoogle = () => {
    setError(null);
    setNotice(null);
    setGoogleError(null);
    setShowGoogleModal(true);
  };

  const handleExecuteGoogleLogin = async (targetEmail, targetName) => {
    const finalEmail = (targetEmail || googleEmail || "").trim();
    const finalName = (targetName || googleName || "").trim();

    if (!finalEmail || !finalEmail.includes("@")) {
      setGoogleError("Please enter a valid Google account email address.");
      return;
    }

    setGoogleBusy(true);
    setGoogleError(null);

    try {
      await loginWithGoogle({
        email: finalEmail,
        name: finalName,
      });

      if (finalName) {
        updateProfile({
          fullName: finalName,
          displayName: finalName.split(" ")[0],
          email: finalEmail,
        });
      }

      setShowGoogleModal(false);
      navigate("/dashboard", { replace: true });
    } catch (err) {
      setGoogleError(err.message || "Failed to sign in with Google SSO. Please verify server connection.");
    } finally {
      setGoogleBusy(false);
    }
  };

  const handleSocialAuth = (provider) => {
    if (provider === "Google") {
      handleOpenGoogle();
      return;
    }
    setNotice(
      `${provider} Single Sign-On (SSO): Identity Provider federation requires integration with the Government National Security Gateway.`
    );
    setError(null);
  };

  const handleQuickFill = () => {
    setEmail("admin@sachet.gov");
    setPassword("Sachet@123");
    setIsSignUp(false);
    setError(null);
    setNotice("Loaded default administrator credentials.");
  };

  return (
    <div className="auth-wrapper font-sans selection:bg-blue-500 selection:text-white bg-black">
      <CyberPattern fixed />
      {/* Background glow and subtle ambient pattern */}
      <div className="absolute top-1/4 left-1/2 -translate-x-1/2 -translate-y-1/2 w-96 h-96 bg-blue-500/15 rounded-full blur-3xl pointer-events-none" />
      <div className="absolute bottom-10 right-10 w-72 h-72 bg-cyan-500/15 rounded-full blur-3xl pointer-events-none" />

      {/* Brand logo & header on top of form */}
      <div className="text-center mb-6 z-10 animate-fadeIn">
        <div className="inline-flex items-center justify-center w-20 h-20 rounded-full bg-slate-950 border-2 border-amber-500/60 shadow-xl shadow-blue-900/40 mb-3 relative overflow-hidden group">
          <img
            src="/sachet-logo.jpg"
            alt="SACHET AI Official Emblem"
            className="w-full h-full object-cover transition-transform duration-300 group-hover:scale-105 rounded-full"
          />
          <span className="absolute top-0 right-1 flex h-3 w-3">
            <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75" />
            <span className="relative inline-flex rounded-full h-3 w-3 bg-emerald-500 border border-slate-950" />
          </span>
        </div>
        <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-slate-900 dark:text-white">
          SACHET AI
        </h1>
        <p className="text-xs uppercase tracking-widest text-slate-500 dark:text-zinc-400 font-semibold mt-1">
          Security-First Document Intelligence
        </p>
      </div>

      {/* Main Uiverse Styled Form Container */}
      <form onSubmit={handleSubmit} className="uiverse-form animate-fadeIn" noValidate>
        {/* Error or Notice banners */}
        {error && (
          <div className="animate-fadeIn">
            <Alert kind="error">{error}</Alert>
          </div>
        )}
        {notice && (
          <div className="animate-fadeIn">
            <Alert kind="info">{notice}</Alert>
          </div>
        )}

        {/* Optional Full Name in Sign Up mode */}
        {isSignUp && (
          <>
            <div className="flex-column">
              <label>Full Name</label>
            </div>
            <div className="inputForm">
              <svg xmlns="http://www.w3.org/2000/svg" width={20} height={20} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} className="shrink-0 text-slate-500 dark:text-zinc-400">
                <path strokeLinecap="round" strokeLinejoin="round" d="M15.75 6a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0zM4.501 20.118a7.5 7.5 0 0114.998 0A17.933 17.933 0 0112 21.75c-2.676 0-5.216-.584-7.499-1.632z" />
              </svg>
              <input
                placeholder="Enter your Full Name"
                className="input"
                type="text"
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
              />
            </div>
          </>
        )}

        {/* Email Field with exact Uiverse icon */}
        <div className="flex-column">
          <label>Email</label>
        </div>
        <div className="inputForm">
          <svg
            xmlns="http://www.w3.org/2000/svg"
            width={20}
            viewBox="0 0 32 32"
            height={20}
            className="shrink-0 text-slate-500 dark:text-zinc-400"
          >
            <g data-name="Layer 3" id="Layer_3">
              <path
                fill="currentColor"
                d="m30.853 13.87a15 15 0 0 0 -29.729 4.082 15.1 15.1 0 0 0 12.876 12.918 15.6 15.6 0 0 0 2.016.13 14.85 14.85 0 0 0 7.715-2.145 1 1 0 1 0 -1.031-1.711 13.007 13.007 0 1 1 5.458-6.529 2.149 2.149 0 0 1 -4.158-.759v-10.856a1 1 0 0 0 -2 0v1.726a8 8 0 1 0 .2 10.325 4.135 4.135 0 0 0 7.83.274 15.2 15.2 0 0 0 .823-7.455zm-14.853 8.13a6 6 0 1 1 6-6 6.006 6.006 0 0 1 -6 6z"
              />
            </g>
          </svg>
          <input
            placeholder="Enter your Email"
            className="input"
            type="email"
            required
            autoComplete="username"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        </div>

        {/* Password Field with exact Uiverse icon */}
        <div className="flex-column">
          <label>Password</label>
        </div>
        <div className="inputForm">
          <svg
            xmlns="http://www.w3.org/2000/svg"
            width={20}
            viewBox="-64 0 512 512"
            height={20}
            className="shrink-0 text-slate-500 dark:text-zinc-400"
          >
            <path
              fill="currentColor"
              d="m336 512h-288c-26.453125 0-48-21.523438-48-48v-224c0-26.476562 21.546875-48 48-48h288c26.453125 0 48 21.523438 48 48v224c0 26.476562-21.546875 48-48 48zm-288-288c-8.8125 0-16 7.167969-16 16v224c0 8.832031 7.1875 16 16 16h288c8.8125 0 16-7.167969 16-16v-224c0-8.832031-7.1875-16-16-16zm0 0"
            />
            <path
              fill="currentColor"
              d="m304 224c-8.832031 0-16-7.167969-16-16v-80c0-52.929688-43.070312-96-96-96s-96 43.070312-96 96v80c0 8.832031-7.167969 16-16 16s-16-7.167969-16-16v-80c0-70.59375 57.40625-128 128-128s128 57.40625 128 128v80c0 8.832031-7.167969 16-16 16zm0 0"
            />
          </svg>
          <input
            placeholder="Enter your Password"
            className="input"
            type="password"
            required
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </div>

        {/* Remember me & Forgot password row */}
        <div className="flex-row">
          <div>
            <input
              type="checkbox"
              id="rememberMe"
              checked={rememberMe}
              onChange={(e) => setRememberMe(e.target.checked)}
              className="rounded text-blue-600 focus:ring-blue-500 cursor-pointer"
            />
            <label htmlFor="rememberMe">Remember me</label>
          </div>
          <span
            className="span"
            role="button"
            tabIndex={0}
            onClick={handleForgotPassword}
            onKeyDown={(e) => e.key === "Enter" && handleForgotPassword()}
          >
            Forgot password?
          </span>
        </div>

        {/* Submit Button */}
        <button type="submit" disabled={busy} className="button-submit">
          {busy ? (
            <span className="flex items-center gap-2">
              <svg className="animate-spin h-4 w-4 text-white" fill="none" viewBox="0 0 24 24">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
              </svg>
              <span>Authenticating...</span>
            </span>
          ) : (
            <span>{isSignUp ? "Sign Up" : "Sign In"}</span>
          )}
        </button>

        {/* Toggle between Sign In and Sign Up */}
        <p className="p">
          {isSignUp ? "Already have an account?" : "Don't have an account?"}{" "}
          <span
            className="span"
            role="button"
            tabIndex={0}
            onClick={() => {
              setIsSignUp(!isSignUp);
              setError(null);
              setNotice(null);
            }}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                setIsSignUp(!isSignUp);
                setError(null);
                setNotice(null);
              }
            }}
          >
            {isSignUp ? "Sign In" : "Sign Up"}
          </span>
        </p>

        {/* Or With divider */}
        <p className="p line">Or With</p>

        {/* Social SSO Buttons */}
        <div className="flex-row">
          <button
            type="button"
            onClick={() => handleSocialAuth("Google")}
            className="btn google relative group overflow-hidden"
            title="Sign in live with Google SSO"
          >
            <svg
              xmlSpace="preserve"
              style={{ enableBackground: "new 0 0 512 512" }}
              viewBox="0 0 512 512"
              y="0px"
              x="0px"
              xmlnsXlink="http://www.w3.org/1999/xlink"
              xmlns="http://www.w3.org/2000/svg"
              id="Layer_1"
              width={20}
              height={20}
              version="1.1"
              className="shrink-0"
            >
              <path
                d="M113.47,309.408L95.648,375.94l-65.139,1.378C11.042,341.211,0,299.9,0,256 c0-42.451,10.324-82.483,28.624-117.732h0.014l57.992,10.632l25.404,57.644c-5.317,15.501-8.215,32.141-8.215,49.456 C103.821,274.792,107.225,292.797,113.47,309.408z"
                style={{ fill: "#FBBB00" }}
              />
              <path
                d="M507.527,208.176C510.467,223.662,512,239.655,512,256c0,18.328-1.927,36.206-5.598,53.451 c-12.462,58.683-45.025,109.925-90.134,146.187l-0.014-0.014l-73.044-3.727l-10.338-64.535 c29.932-17.554,53.324-45.025,65.646-77.911h-136.89V208.176h138.887L507.527,208.176L507.527,208.176z"
                style={{ fill: "#518EF8" }}
              />
              <path
                d="M416.253,455.624l0.014,0.014C372.396,490.901,316.666,512,256,512 c-97.491,0-182.252-54.491-225.491-134.681l82.961-67.91c21.619,57.698,77.278,98.771,142.53,98.771 c28.047,0,54.323-7.582,76.87-20.818L416.253,455.624z"
                style={{ fill: "#28B446" }}
              />
              <path
                d="M419.404,58.936l-82.933,67.896c-23.335-14.586-50.919-23.012-80.471-23.012 c-66.729,0-123.429,42.957-143.965,102.724l-83.397-68.276h-0.014C71.23,56.123,157.06,0,256,0 C318.115,0,375.068,22.126,419.404,58.936z"
                style={{ fill: "#F14336" }}
              />
            </svg>
            <span className="font-semibold text-slate-900 dark:text-white">Google</span>
            <span className="absolute top-1 right-2 inline-flex items-center px-1.5 py-0.2 text-[9px] font-bold text-emerald-400 bg-emerald-950/80 border border-emerald-500/30 rounded-full">
              LIVE
            </span>
          </button>

          <button
            type="button"
            onClick={() => handleSocialAuth("Apple")}
            className="btn apple"
            title="Sign in with Apple SSO"
          >
            <svg
              xmlSpace="preserve"
              style={{ enableBackground: "new 0 0 22.773 22.773" }}
              viewBox="0 0 22.773 22.773"
              y="0px"
              x="0px"
              xmlnsXlink="http://www.w3.org/1999/xlink"
              xmlns="http://www.w3.org/2000/svg"
              id="Capa_1"
              width={20}
              height={20}
              version="1.1"
              className="shrink-0 fill-current text-slate-800 dark:text-white"
            >
              <g>
                <g>
                  <path d="M15.769,0c0.053,0,0.106,0,0.162,0c0.13,1.606-0.483,2.806-1.228,3.675c-0.731,0.863-1.732,1.7-3.351,1.573 c-0.108-1.583,0.506-2.694,1.25-3.561C13.292,0.879,14.557,0.16,15.769,0z" />
                  <path d="M20.67,16.716c0,0.016,0,0.03,0,0.045c-0.455,1.378-1.104,2.559-1.896,3.655c-0.723,0.995-1.609,2.334-3.191,2.334 c-1.367,0-2.275-0.879-3.676-0.903c-1.482-0.024-2.297,0.735-3.652,0.926c-0.155,0-0.31,0-0.462,0 c-0.995-0.144-1.798-0.932-2.383-1.642c-1.725-2.098-3.058-4.808-3.306-8.276c0-0.34,0-0.679,0-1.019 c0.105-2.482,1.311-4.5,2.914-5.478c0.846-0.52,2.009-0.963,3.304-0.765c0.555,0.086,1.122,0.276,1.619,0.464 c0.471,0.181,1.06,0.502,1.618,0.485c0.378-0.011,0.754-0.208,1.135-0.347c1.116-0.403,2.21-0.865,3.652-0.648 c1.733,0.262,2.963,1.032,3.723,2.22c-1.466,0.933-2.625,2.339-2.427,4.74C17.818,14.688,19.086,15.964,20.67,16.716z" />
                </g>
              </g>
            </svg>
            <span>Apple</span>
          </button>
        </div>

        {/* Quick demo credentials helper */}
        <div className="mt-2 pt-3 border-t border-slate-100 dark:border-zinc-800 flex items-center justify-between text-xs text-slate-500 dark:text-zinc-400">
          <span>Demo credentials:</span>
          <button
            type="button"
            onClick={handleQuickFill}
            className="px-2 py-0.5 rounded-md bg-blue-50 dark:bg-zinc-800 text-blue-600 dark:text-blue-400 font-mono text-[11px] hover:bg-blue-100 dark:hover:bg-zinc-700 transition-colors cursor-pointer border border-blue-200 dark:border-zinc-700"
            title="Auto-fill admin@sachet.gov / Sachet@123"
          >
            admin@sachet.gov
          </button>
        </div>
      </form>

      {/* Security boundary footer note */}
      <div className="text-center mt-6 text-xs text-slate-500 dark:text-zinc-500 flex items-center justify-center gap-1.5 z-10">
        <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" width={14} height={14} className="text-emerald-500">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12.75L11.25 15 15 9.75M21 12c0 4.97-4.03 9-9 9s-9-4.03-9-9 4.03-9 9-9c1.19 0 2.33.22 3.36.62M21 12a9 9 0 00-9-9" />
        </svg>
        <span>Server-side RBAC · Audit-logged sessions · Zero data leakage</span>
      </div>

      {/* =========================================================================
          Live Google Sign-In Interactive Modal Overlay
         ========================================================================= */}
      {showGoogleModal && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-md p-4 animate-fadeIn"
          role="dialog"
          aria-modal="true"
          aria-labelledby="google-modal-title"
          onClick={(e) => {
            if (e.target === e.currentTarget && !googleBusy) {
              setShowGoogleModal(false);
            }
          }}
        >
          <div
            className="relative w-full max-w-md bg-zinc-950 border border-slate-700/80 rounded-2xl p-6 sm:p-7 shadow-2xl text-left overflow-hidden"
            style={{
              boxShadow: "0 20px 45px -10px rgba(0, 0, 0, 0.8), 0 0 20px rgba(56, 189, 248, 0.15)",
            }}
          >
            {/* Modal header with Google Logo */}
            <div className="flex items-start justify-between mb-5">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-full bg-white flex items-center justify-center shadow-md p-2">
                  <svg
                    viewBox="0 0 512 512"
                    width={22}
                    height={22}
                    xmlns="http://www.w3.org/2000/svg"
                  >
                    <path
                      d="M113.47,309.408L95.648,375.94l-65.139,1.378C11.042,341.211,0,299.9,0,256 c0-42.451,10.324-82.483,28.624-117.732h0.014l57.992,10.632l25.404,57.644c-5.317,15.501-8.215,32.141-8.215,49.456 C103.821,274.792,107.225,292.797,113.47,309.408z"
                      fill="#FBBB00"
                    />
                    <path
                      d="M507.527,208.176C510.467,223.662,512,239.655,512,256c0,18.328-1.927,36.206-5.598,53.451 c-12.462,58.683-45.025,109.925-90.134,146.187l-0.014-0.014l-73.044-3.727l-10.338-64.535 c29.932-17.554,53.324-45.025,65.646-77.911h-136.89V208.176h138.887L507.527,208.176L507.527,208.176z"
                      fill="#518EF8"
                    />
                    <path
                      d="M416.253,455.624l0.014,0.014C372.396,490.901,316.666,512,256,512 c-97.491,0-182.252-54.491-225.491-134.681l82.961-67.91c21.619,57.698,77.278,98.771,142.53,98.771 c28.047,0,54.323-7.582,76.87-20.818L416.253,455.624z"
                      fill="#28B446"
                    />
                    <path
                      d="M419.404,58.936l-82.933,67.896c-23.335-14.586-50.919-23.012-80.471-23.012 c-66.729,0-123.429,42.957-143.965,102.724l-83.397-68.276h-0.014C71.23,56.123,157.06,0,256,0 C318.115,0,375.068,22.126,419.404,58.936z"
                      fill="#F14336"
                    />
                  </svg>
                </div>
                <div>
                  <h3 id="google-modal-title" className="text-lg font-bold text-white leading-tight">
                    Sign in with Google
                  </h3>
                  <p className="text-xs text-slate-400 mt-0.5">
                    Connect to SACHET AI via verified Google SSO
                  </p>
                </div>
              </div>

              <button
                type="button"
                onClick={() => setShowGoogleModal(false)}
                disabled={googleBusy}
                className="text-slate-400 hover:text-white p-1 rounded-lg hover:bg-zinc-800 transition-colors"
                aria-label="Close Google Sign-in modal"
              >
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            {/* Error banner inside modal */}
            {googleError && (
              <div className="mb-4">
                <Alert kind="error">{googleError}</Alert>
              </div>
            )}

            {/* Account Selection Options */}
            <div className="space-y-3 mb-5">
              <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                Choose an account
              </p>

              {/* Quick option: System Administrator Google SSO */}
              <div
                role="button"
                tabIndex={0}
                onClick={() => handleExecuteGoogleLogin("admin@sachet.gov", "System Administrator")}
                onKeyDown={(e) => e.key === "Enter" && handleExecuteGoogleLogin("admin@sachet.gov", "System Administrator")}
                className="flex items-center justify-between p-3.5 rounded-xl border border-slate-700/70 bg-zinc-900/80 hover:bg-zinc-800/80 hover:border-cyan-500/50 cursor-pointer transition-all duration-150 group"
              >
                <div className="flex items-center gap-3">
                  <div className="w-9 h-9 rounded-full bg-gradient-to-tr from-blue-600 to-cyan-500 flex items-center justify-center font-bold text-white text-sm shadow-md">
                    SA
                  </div>
                  <div>
                    <div className="text-sm font-semibold text-slate-100 group-hover:text-cyan-400 transition-colors">
                      System Administrator
                    </div>
                    <div className="text-xs text-slate-400">admin@sachet.gov</div>
                  </div>
                </div>
                <span className="text-[10px] font-semibold uppercase px-2 py-0.5 rounded-full bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">
                  HQ Admin
                </span>
              </div>

              {/* Quick option: Operations Officer Google SSO */}
              <div
                role="button"
                tabIndex={0}
                onClick={() => handleExecuteGoogleLogin("ops.officer@gmail.com", "Operations Officer")}
                onKeyDown={(e) => e.key === "Enter" && handleExecuteGoogleLogin("ops.officer@gmail.com", "Operations Officer")}
                className="flex items-center justify-between p-3.5 rounded-xl border border-slate-700/70 bg-zinc-900/80 hover:bg-zinc-800/80 hover:border-cyan-500/50 cursor-pointer transition-all duration-150 group"
              >
                <div className="flex items-center gap-3">
                  <div className="w-9 h-9 rounded-full bg-gradient-to-tr from-emerald-600 to-teal-500 flex items-center justify-center font-bold text-white text-sm shadow-md">
                    OO
                  </div>
                  <div>
                    <div className="text-sm font-semibold text-slate-100 group-hover:text-cyan-400 transition-colors">
                      Operations Officer
                    </div>
                    <div className="text-xs text-slate-400">ops.officer@gmail.com</div>
                  </div>
                </div>
                <span className="text-[10px] font-semibold uppercase px-2 py-0.5 rounded-full bg-cyan-500/20 text-cyan-400 border border-cyan-500/30">
                  Officer
                </span>
              </div>
            </div>

            {/* Custom Google Account Section */}
            <div className="pt-4 border-t border-zinc-800">
              <p className="text-xs font-semibold uppercase tracking-wider text-slate-400 mb-3">
                Or sign in with another Google account
              </p>

              <div className="space-y-3">
                <div>
                  <label className="block text-xs font-medium text-slate-300 mb-1">
                    Google Email Address
                  </label>
                  <input
                    type="email"
                    placeholder="e.g. officer.name@gmail.com"
                    value={googleEmail}
                    onChange={(e) => setGoogleEmail(e.target.value)}
                    disabled={googleBusy}
                    className="w-full px-3.5 py-2.5 rounded-xl bg-zinc-900 border border-slate-700 text-white placeholder-slate-500 text-sm focus:outline-none focus:border-cyan-400 focus:ring-1 focus:ring-cyan-400 transition-colors"
                  />
                </div>

                <div>
                  <label className="block text-xs font-medium text-slate-300 mb-1">
                    Your Name (Optional)
                  </label>
                  <input
                    type="text"
                    placeholder="e.g. Gourav Kumar"
                    value={googleName}
                    onChange={(e) => setGoogleName(e.target.value)}
                    disabled={googleBusy}
                    className="w-full px-3.5 py-2.5 rounded-xl bg-zinc-900 border border-slate-700 text-white placeholder-slate-500 text-sm focus:outline-none focus:border-cyan-400 focus:ring-1 focus:ring-cyan-400 transition-colors"
                  />
                </div>

                <button
                  type="button"
                  disabled={googleBusy}
                  onClick={() => handleExecuteGoogleLogin()}
                  className="w-full mt-2 py-3 px-4 rounded-xl font-semibold text-sm bg-gradient-to-r from-blue-600 to-cyan-600 hover:from-blue-500 hover:to-cyan-500 text-white shadow-lg shadow-cyan-600/25 flex items-center justify-center gap-2 cursor-pointer transition-all disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  {googleBusy ? (
                    <>
                      <svg className="animate-spin h-4 w-4 text-white" fill="none" viewBox="0 0 24 24">
                        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
                      </svg>
                      <span>Authenticating with Google...</span>
                    </>
                  ) : (
                    <>
                      <svg className="w-4 h-4 fill-current" viewBox="0 0 24 24">
                        <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 14.5v-9l6 4.5-6 4.5z" />
                      </svg>
                      <span>Continue with this Google Account</span>
                    </>
                  )}
                </button>
              </div>
            </div>

            <div className="mt-4 text-center">
              <button
                type="button"
                onClick={() => setShowGoogleModal(false)}
                disabled={googleBusy}
                className="text-xs text-slate-400 hover:text-slate-200 transition-colors cursor-pointer"
              >
                Cancel and return to standard sign-in
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}