
import { BrowserRouter, Navigate, Route, Routes, useNavigate } from "react-router-dom";
import SavedQuotes from "./components/savedQuotes";
import RiskDetailsForm from "./components/RiskDetailsForm";
import CoverageBuilder from "./components/CoverageBuilder";
import CoverageComparison from "./components/CoverageComparison";
import LoginPage from "./features/auth/LoginPage";
import "./App.css";

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Navigate to="/login" replace />} />
        <Route path="/login" element={<LoginRoute />} />
        <Route path="/risk-details" element={<RiskDetailsForm />} />
        <Route path="/coverage" element={<CoverageBuilder />} />
        <Route path="/comparison" element={<CoverageComparison />} />
        <Route path="/saved-quotes" element={<SavedQuotes />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

function LoginRoute() {
  const navigate = useNavigate();

  return <LoginPage onSignIn={() => navigate("/risk-details", { replace: true })} />;
}

export default App;
