
import { BrowserRouter, Navigate, Route, Routes, useNavigate, Outlet } from "react-router-dom";
import SavedQuotes from "./components/savedQuotes";
import RiskDetailsForm from "./components/RiskDetailsForm";
import CoverageBuilder from "./components/CoverageBuilder";
import CoverageComparison from "./components/CoverageComparison";
import LoginPage from "./features/auth/LoginPage";
import "./App.css";
import type { QuoteDraft } from './types/savedQuotes';
import ProtectedRoute from "./components/ProtectedRoute";


function AppRoutes() {
  const navigate = useNavigate();

  return (
    <Routes>
      <Route path="/" element={<Navigate to="/login" replace />} />
      <Route path="/login" element={<LoginRoute />} />

      {/*all pages require authentication*/}
      <Route 
        element={
          <ProtectedRoute>
            <Outlet />
          </ProtectedRoute>
        }
      >
        <Route path="/risk-details" element={<RiskDetailsForm />} />
        <Route path="/coverage" element={<CoverageBuilder />} />
        <Route path="/comparison" element={<CoverageComparison />} />
       
<Route
  path="/saved-quotes"
  element={
    <SavedQuotes
      onStartNew={() => navigate("/risk-details")}
      onDuplicate={(draft: QuoteDraft) =>
        navigate("/risk-details", {
          state: { duplicateDraft: draft },
        })
      }
    />
  }
/>

      </Route>
      
      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  );
}

function LoginRoute() {
  const navigate = useNavigate();

  return (
    <LoginPage
      onSignIn={() => navigate("/risk-details", { replace: true })}
    />
  );
}

function App() {
  return (
    <BrowserRouter>
      <AppRoutes />
    </BrowserRouter>
  );
}

export default App;
