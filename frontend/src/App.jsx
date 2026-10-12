import { Routes, Route, Navigate } from "react-router-dom";
import { useAuth } from "./context/AuthContext";
import Layout from "./components/Layout";
import Login from "./pages/Login";
import Dashboard from "./pages/Dashboard";
import Assistant from "./pages/Assistant";
import KnowledgeVault from "./pages/KnowledgeVault";
import Documents from "./pages/Documents";
import DocumentDetails from "./pages/DocumentDetails";
import DocumentViewer from "./pages/DocumentViewer";
import Studio from "./pages/Studio";
import Search from "./pages/Search";
import Generated from "./pages/Generated";
import Audit from "./pages/Audit";
import Security from "./pages/Security";
import Settings from "./pages/Settings";

function Protected({ children }) {
  const { auth } = useAuth();
  return auth ? children : <Navigate to="/login" replace />;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/*" element={
        <Protected>
          <Layout>
            <Routes>
              <Route path="/" element={<Navigate to="/dashboard" replace />} />
              <Route path="/dashboard" element={<Dashboard />} />
              <Route path="/assistant" element={<Assistant />} />
              <Route path="/knowledge" element={<KnowledgeVault />} />
              <Route path="/documents" element={<Documents />} />
              <Route path="/documents/:id" element={<DocumentDetails />} />
              <Route path="/documents/:id/view" element={<DocumentViewer />} />
              <Route path="/studio" element={<Studio />} />
              <Route path="/search" element={<Search />} />
              <Route path="/generated" element={<Generated />} />
              <Route path="/audit" element={<Audit />} />
              <Route path="/security" element={<Security />} />
              <Route path="/settings" element={<Settings />} />
              <Route path="*" element={<Navigate to="/dashboard" replace />} />
            </Routes>
          </Layout>
        </Protected>
      } />
    </Routes>
  );
}
