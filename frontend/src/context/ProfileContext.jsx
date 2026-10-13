import { createContext, useContext, useState, useEffect, useCallback, useMemo } from "react";
import { useAuth } from "./AuthContext";

const ProfileContext = createContext(null);

const AVATAR_COLORS = [
  { id: "cyan", bg: "linear-gradient(135deg, #0284c7 0%, #0369a1 100%)", text: "#e0f2fe", border: "#38bdf8" },
  { id: "emerald", bg: "linear-gradient(135deg, #059669 0%, #047857 100%)", text: "#d1fae5", border: "#34d399" },
  { id: "purple", bg: "linear-gradient(135deg, #7c3aed 0%, #6d28d9 100%)", text: "#ede9fe", border: "#a78bfa" },
  { id: "amber", bg: "linear-gradient(135deg, #d97706 0%, #b45309 100%)", text: "#fef3c7", border: "#fbbf24" },
  { id: "rose", bg: "linear-gradient(135deg, #e11d48 0%, #be123c 100%)", text: "#ffe4e6", border: "#fb7185" },
];

function isUUID(str) {
  if (!str) return false;
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(str);
}

function generateDefaultProfile(userId, roles = []) {
  let defaultFullName = "Authorized Officer";
  let defaultDisplayName = "Officer";
  let defaultEmail = "officer@sachet.local";

  if (userId) {
    if (userId.includes("@")) {
      const prefix = userId.split("@")[0];
      const capitalized = prefix.charAt(0).toUpperCase() + prefix.slice(1);
      defaultFullName = `${capitalized} Officer`;
      defaultDisplayName = capitalized;
      defaultEmail = userId;
    } else if (!isUUID(userId)) {
      const clean = userId.replace(/[._-]/g, " ");
      defaultFullName = clean.charAt(0).toUpperCase() + clean.slice(1);
      defaultDisplayName = defaultFullName.split(" ")[0];
    }
  }

  const isBoss = roles.includes("ADMIN") || roles.includes("SECURITY_ADMIN");
  const defaultTitle = isBoss ? "Security Administrator" : "Document Intelligence Analyst";

  return {
    fullName: defaultFullName,
    displayName: defaultDisplayName,
    title: defaultTitle,
    department: "Operations & Intelligence",
    email: defaultEmail,
    phone: "+91 (11) 2301-0000",
    bio: "Authorized personnel managing sensitive knowledge repositories and AI intelligence.",
    avatarColor: "cyan",
    clearance: "INTERNAL",
    lastUpdated: new Date().toISOString(),
  };
}

export function ProfileProvider({ children }) {
  const { auth } = useAuth();
  const userId = auth?.userId || "default_user";

  const storageKey = useMemo(() => `sachet.profile.${userId}`, [userId]);

  const [profile, setProfile] = useState(() => {
    try {
      const saved = localStorage.getItem(`sachet.profile.${userId}`);
      if (saved) {
        return { ...generateDefaultProfile(userId, auth?.roles), ...JSON.parse(saved) };
      }
    } catch {
      // fallback to generated
    }
    return generateDefaultProfile(userId, auth?.roles);
  });

  const [isModalOpen, setIsModalOpen] = useState(false);

  // Sync profile when user changes
  useEffect(() => {
    try {
      const saved = localStorage.getItem(`sachet.profile.${userId}`);
      if (saved) {
        setProfile({ ...generateDefaultProfile(userId, auth?.roles), ...JSON.parse(saved) });
      } else {
        const fresh = generateDefaultProfile(userId, auth?.roles);
        setProfile(fresh);
        localStorage.setItem(`sachet.profile.${userId}`, JSON.stringify(fresh));
      }
    } catch {
      setProfile(generateDefaultProfile(userId, auth?.roles));
    }
  }, [userId, auth?.roles]);

  const updateProfile = useCallback((updates) => {
    setProfile((prev) => {
      const next = {
        ...prev,
        ...updates,
        lastUpdated: new Date().toISOString(),
      };
      try {
        localStorage.setItem(storageKey, JSON.stringify(next));
      } catch (err) {
        console.error("Failed to save profile to localStorage:", err);
      }
      window.dispatchEvent(new CustomEvent("sachet:profile-updated", { detail: next }));
      return next;
    });
  }, [storageKey]);

  const resetProfile = useCallback(() => {
    const fresh = generateDefaultProfile(userId, auth?.roles);
    setProfile(fresh);
    try {
      localStorage.setItem(storageKey, JSON.stringify(fresh));
    } catch (err) {
      console.error("Failed to reset profile:", err);
    }
    window.dispatchEvent(new CustomEvent("sachet:profile-updated", { detail: fresh }));
  }, [userId, auth?.roles, storageKey]);

  const getDisplayName = useCallback(() => {
    if (profile?.displayName?.trim()) return profile.displayName.trim();
    if (profile?.fullName?.trim()) return profile.fullName.trim();
    if (auth?.userId && !isUUID(auth.userId) && !auth.userId.includes("@")) {
      return auth.userId;
    }
    return "Officer";
  }, [profile, auth?.userId]);

  const getInitials = useCallback(() => {
    const name = profile?.fullName?.trim() || profile?.displayName?.trim() || "";
    if (name) {
      const parts = name.split(/\s+/).filter(Boolean);
      if (parts.length >= 2) {
        return (parts[0][0] + parts[1][0]).toUpperCase();
      }
      return name.slice(0, 2).toUpperCase();
    }
    return "AO";
  }, [profile]);

  const getAvatarStyle = useCallback(() => {
    const colorId = profile?.avatarColor || "cyan";
    const found = AVATAR_COLORS.find((c) => c.id === colorId) || AVATAR_COLORS[0];
    return {
      background: found.bg,
      color: found.text,
      border: `1.5px solid ${found.border}`,
      boxShadow: `0 0 12px ${found.border}44`,
    };
  }, [profile?.avatarColor]);

  const openProfile = useCallback(() => setIsModalOpen(true), []);
  const closeProfile = useCallback(() => setIsModalOpen(false), []);

  const value = useMemo(() => ({
    profile,
    updateProfile,
    resetProfile,
    getDisplayName,
    getInitials,
    getAvatarStyle,
    avatarColors: AVATAR_COLORS,
    isModalOpen,
    openProfile,
    closeProfile,
  }), [
    profile,
    updateProfile,
    resetProfile,
    getDisplayName,
    getInitials,
    getAvatarStyle,
    isModalOpen,
    openProfile,
    closeProfile,
  ]);

  return (
    <ProfileContext.Provider value={value}>
      {children}
    </ProfileContext.Provider>
  );
}

export function useProfile() {
  const ctx = useContext(ProfileContext);
  if (!ctx) {
    throw new Error("useProfile must be used within a ProfileProvider");
  }
  return ctx;
}
