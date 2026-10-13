import { useState, useEffect } from "react";
import { useProfile } from "../context/ProfileContext";
import { useAuth } from "../context/AuthContext";
import { Button, Input, ClassificationBadge } from "./UI";

export default function ProfileModal() {
  const { auth } = useAuth();
  const {
    profile,
    updateProfile,
    resetProfile,
    isModalOpen,
    closeProfile,
    avatarColors,
    getInitials,
  } = useProfile();

  const [formData, setFormData] = useState({ ...profile });
  const [savedToast, setSavedToast] = useState(false);

  useEffect(() => {
    if (isModalOpen) {
      setFormData({ ...profile });
      setSavedToast(false);
    }
  }, [isModalOpen, profile]);

  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === "Escape" && isModalOpen) {
        closeProfile();
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [isModalOpen, closeProfile]);

  if (!isModalOpen) return null;

  const handleChange = (field, value) => {
    setFormData((prev) => ({ ...prev, [field]: value }));
  };

  const handleSave = (e) => {
    e.preventDefault();
    updateProfile(formData);
    setSavedToast(true);
    setTimeout(() => {
      setSavedToast(false);
      closeProfile();
    }, 600);
  };

  const handleReset = () => {
    resetProfile();
    closeProfile();
  };

  const activeColor = avatarColors.find((c) => c.id === formData.avatarColor) || avatarColors[0];

  return (
    <div
      style={{
        position: "fixed",
        inset: 0,
        zIndex: 1000,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        padding: "16px",
        background: "rgba(0, 0, 0, 0.75)",
        backdropFilter: "blur(10px)",
        WebkitBackdropFilter: "blur(10px)",
      }}
      onClick={(e) => {
        if (e.target === e.currentTarget) closeProfile();
      }}
      role="dialog"
      aria-modal="true"
      aria-labelledby="profile-modal-title"
    >
      <div
        style={{
          width: "100%",
          maxWidth: "600px",
          background: "linear-gradient(145deg, rgba(15, 23, 42, 0.95) 0%, rgba(10, 16, 28, 0.98) 100%)",
          border: "1px solid rgba(56, 189, 248, 0.35)",
          boxShadow: "0 25px 50px -12px rgba(0, 0, 0, 0.8), 0 0 30px rgba(56, 189, 248, 0.15)",
          borderRadius: "16px",
          overflow: "hidden",
          color: "#f8fafc",
          animation: "ui-fade-in 0.2s ease-out",
        }}
      >
        {/* Header with live avatar badge preview */}
        <div
          style={{
            padding: "24px 28px 20px",
            borderBottom: "1px solid rgba(56, 189, 248, 0.2)",
            background: "rgba(56, 189, 248, 0.04)",
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
          }}
        >
          <div style={{ display: "flex", alignItems: "center", gap: "16px" }}>
            <div
              style={{
                width: "56px",
                height: "56px",
                borderRadius: "50%",
                background: activeColor.bg,
                color: activeColor.text,
                border: `2px solid ${activeColor.border}`,
                boxShadow: `0 0 16px ${activeColor.border}55`,
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                fontSize: "20px",
                fontWeight: 700,
                flexShrink: 0,
              }}
            >
              {getInitials()}
            </div>
            <div>
              <div style={{ display: "flex", alignItems: "center", gap: "8px", flexWrap: "wrap" }}>
                <h2
                  id="profile-modal-title"
                  style={{
                    margin: 0,
                    fontSize: "18px",
                    fontWeight: 700,
                    color: "#ffffff",
                    letterSpacing: "-0.01em",
                  }}
                >
                  {formData.fullName || "User Profile"}
                </h2>
                <ClassificationBadge classification={formData.clearance || "INTERNAL"} />
              </div>
              <p style={{ margin: "4px 0 0", fontSize: "13px", color: "#94a3b8" }}>
                {formData.title || "Authorized Personnel"} • {formData.department || "SACHET AI"}
              </p>
            </div>
          </div>

          <button
            type="button"
            onClick={closeProfile}
            style={{
              background: "rgba(255, 255, 255, 0.06)",
              border: "1px solid rgba(255, 255, 255, 0.12)",
              color: "#94a3b8",
              width: "32px",
              height: "32px",
              borderRadius: "8px",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              cursor: "pointer",
              fontSize: "18px",
              transition: "all 0.15s ease",
            }}
            onMouseOver={(e) => {
              e.currentTarget.style.color = "#ffffff";
              e.currentTarget.style.borderColor = "#38bdf8";
            }}
            onMouseOut={(e) => {
              e.currentTarget.style.color = "#94a3b8";
              e.currentTarget.style.borderColor = "rgba(255, 255, 255, 0.12)";
            }}
            aria-label="Close profile modal"
          >
            ✕
          </button>
        </div>

        {/* Modal Form Content */}
        <form onSubmit={handleSave} style={{ padding: "24px 28px" }}>
          {savedToast && (
            <div
              style={{
                marginBottom: "16px",
                padding: "10px 14px",
                background: "rgba(34, 197, 94, 0.2)",
                border: "1px solid rgba(34, 197, 94, 0.5)",
                borderRadius: "8px",
                color: "#86efac",
                fontSize: "13px",
                fontWeight: 600,
                display: "flex",
                alignItems: "center",
                gap: "8px",
              }}
            >
              ✓ Profile saved successfully! Updating interface...
            </div>
          )}

          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "16px", marginBottom: "16px" }}>
            <Input
              label="Full Name"
              value={formData.fullName}
              onChange={(e) => handleChange("fullName", e.target.value)}
              placeholder="e.g. Commander Vikram Malhotra"
              required
            />
            <Input
              label="Display / Call Name"
              value={formData.displayName}
              onChange={(e) => handleChange("displayName", e.target.value)}
              placeholder="e.g. Vikram (used in greetings)"
              hint="Shown in dashboard greeting & messages"
              required
            />
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "16px", marginBottom: "16px" }}>
            <Input
              label="Rank / Designation"
              value={formData.title}
              onChange={(e) => handleChange("title", e.target.value)}
              placeholder="e.g. Senior Operations Officer"
            />
            <Input
              label="Department / Directorate"
              value={formData.department}
              onChange={(e) => handleChange("department", e.target.value)}
              placeholder="e.g. Telecom Intelligence Cell"
            />
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "16px", marginBottom: "16px" }}>
            <Input
              label="Official Email"
              type="email"
              value={formData.email}
              onChange={(e) => handleChange("email", e.target.value)}
              placeholder="e.g. v.malhotra@gov.in"
            />
            <Input
              label="Extension / Secure Phone"
              value={formData.phone}
              onChange={(e) => handleChange("phone", e.target.value)}
              placeholder="e.g. +91 11 2301-8400"
            />
          </div>

          {/* Avatar Color Picker */}
          <div style={{ marginBottom: "20px" }}>
            <label
              style={{
                display: "block",
                fontSize: "13px",
                fontWeight: 600,
                color: "#cbd5e1",
                marginBottom: "8px",
              }}
            >
              Avatar Theme & Color Palette
            </label>
            <div style={{ display: "flex", gap: "10px" }}>
              {avatarColors.map((color) => {
                const isSelected = formData.avatarColor === color.id;
                return (
                  <button
                    key={color.id}
                    type="button"
                    onClick={() => handleChange("avatarColor", color.id)}
                    style={{
                      width: "36px",
                      height: "36px",
                      borderRadius: "50%",
                      background: color.bg,
                      border: isSelected ? `3px solid #ffffff` : `1.5px solid ${color.border}`,
                      boxShadow: isSelected ? `0 0 12px ${color.border}` : "none",
                      cursor: "pointer",
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                      color: "#ffffff",
                      fontSize: "14px",
                      transition: "all 0.15s ease",
                      transform: isSelected ? "scale(1.15)" : "scale(1)",
                    }}
                    title={color.id}
                  >
                    {isSelected && "✓"}
                  </button>
                );
              })}
            </div>
          </div>

          {/* Session Token Details Notice */}
          <div
            style={{
              padding: "12px 14px",
              borderRadius: "8px",
              background: "rgba(56, 189, 248, 0.06)",
              border: "1px solid rgba(56, 189, 248, 0.2)",
              fontSize: "12px",
              color: "#94a3b8",
              marginBottom: "24px",
              lineHeight: 1.5,
            }}
          >
            <strong style={{ color: "#38bdf8" }}>Verified Identity:</strong> Session authenticated under user token{" "}
            <code style={{ color: "#f8fafc", fontFamily: "monospace" }}>{auth?.userId}</code>.
            Roles: {(auth?.roles || ["USER"]).join(", ")}. Changes save locally to your profile preferences.
          </div>

          {/* Action Buttons */}
          <div
            style={{
              display: "flex",
              alignItems: "center",
              justifyContent: "space-between",
              borderTop: "1px solid rgba(255, 255, 255, 0.1)",
              paddingTop: "18px",
            }}
          >
            <Button variant="ghost" size="sm" type="button" onClick={handleReset}>
              Reset to Defaults
            </Button>
            <div style={{ display: "flex", gap: "10px" }}>
              <Button variant="secondary" size="md" type="button" onClick={closeProfile}>
                Cancel
              </Button>
              <Button variant="primary" size="md" type="submit">
                Save Profile Changes
              </Button>
            </div>
          </div>
        </form>
      </div>
    </div>
  );
}
