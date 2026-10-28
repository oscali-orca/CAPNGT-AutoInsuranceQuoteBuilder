
import { BrowserRouter, Navigate, Route, Routes, useNavigate } from "react-router-dom";
import SavedQuotes from "./components/savedQuotes";
import RiskDetailsForm from "./components/RiskDetailsForm";
import CoverageBuilder from "./components/CoverageBuilder";
import CoverageComparison from "./components/CoverageComparison";
import LoginPage from "./features/auth/LoginPage";
import "./App.css";

function AppRoutes() {
  const navigate = useNavigate();

  return (
    <Routes>
      <Route path="/" element={<Navigate to="/login" replace />} />
      <Route path="/login" element={<LoginRoute />} />
      <Route path="/risk-details" element={<RiskDetailsForm />} />
      <Route path="/coverage" element={<CoverageBuilder />} />
      <Route path="/comparison" element={<CoverageComparison />} />
      <Route
        path="/saved-quotes"
        element={
          <SavedQuotes
            onStartNew={() => navigate("/risk-details")}
          />
        }
      />
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
