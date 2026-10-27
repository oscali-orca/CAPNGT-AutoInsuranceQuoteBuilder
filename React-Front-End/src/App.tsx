
import { BrowserRouter, Navigate, Route, Routes, useNavigate } from "react-router-dom";
import SavedQuotes from "./components/savedQuotes";
import RiskDetailsForm from "./components/RiskDetailsForm";
import CoverageBuilder from "./components/CoverageBuilder";
import CoverageComparison from "./components/CoverageComparison";
import "./App.css";

function AppRoutes() {
  const navigate = useNavigate();

  return (
    <Routes>
      <Route path="/" element={<Navigate to="/risk-details" replace />} />
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
    </Routes>
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
