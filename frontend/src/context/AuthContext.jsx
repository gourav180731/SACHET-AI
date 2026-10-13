import { createContext, useContext, useEffect, useState, useCallback } from "react";
import { authApi } from "../api";
import { setToken } from "../api/client";

const AuthContext = createContext(null);

function parseJwt(token) {
  try {
    const payload = token.split(".")[1];
    return JSON.parse(atob(payload.replace(/-/g, "+").replace(/_/g, "/")));
  } catch {
    return {};
  }
}

export function AuthProvider({ children }) {
  const [auth, setAuth] = useState(() => {
    const token = sessionStorage.getItem("sachet.token");
    const roles = JSON.parse(sessionStorage.getItem("sachet.roles") || "[]");
    if (token) setToken(token);
    return token ? { token, roles, userId: sessionStorage.getItem("sachet.user") || "user" } : null;
  });

  useEffect(() => {
    setToken(auth?.token || null);
    if (!auth?.token) sessionStorage.removeItem("sachet.token");
  }, [auth]);

  useEffect(() => {
    const handler = () => { setToken(null); setAuth(null); };
    window.addEventListener("sachet:unauthorized", handler);
    return () => window.removeEventListener("sachet:unauthorized", handler);
  }, []);

  const login = useCallback(async (email, password) => {
    const res = await authApi.login(email, password);
    const claims = parseJwt(res.accessToken);
    const userId = claims.sub || email;
    sessionStorage.setItem("sachet.token", res.accessToken);
    sessionStorage.setItem("sachet.roles", JSON.stringify(res.roles || []));
    sessionStorage.setItem("sachet.user", userId);
    setToken(res.accessToken); // synchronous: child page effects run before provider effects
    setAuth({ token: res.accessToken, roles: res.roles || [], userId });
    return res;
  }, []);

  const loginWithGoogle = useCallback(async (payload) => {
    const res = await authApi.googleLogin(payload);
    const claims = parseJwt(res.accessToken);
    const userId = claims.sub || payload.email || "google-user";
    sessionStorage.setItem("sachet.token", res.accessToken);
    sessionStorage.setItem("sachet.roles", JSON.stringify(res.roles || []));
    sessionStorage.setItem("sachet.user", userId);
    setToken(res.accessToken);
    setAuth({ token: res.accessToken, roles: res.roles || [], userId });
    return res;
  }, []);

  const logout = useCallback(() => {
    sessionStorage.removeItem("sachet.token");
    sessionStorage.removeItem("sachet.roles");
    sessionStorage.removeItem("sachet.user");
    setToken(null);
    setAuth(null);
  }, []);

  return (
    <AuthContext.Provider value={{ auth, login, loginWithGoogle, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() { return useContext(AuthContext); }
