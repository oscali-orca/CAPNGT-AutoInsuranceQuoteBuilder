
import { useCallback, useEffect, useState } from "react";
import { BrowserRouter, Navigate, Outlet, Route, Routes, useNavigate } from "react-router-dom";
import SavedQuotes from "./components/savedQuotes";
import RiskDetailsForm from "./components/RiskDetailsForm";
import CoverageBuilder from "./components/CoverageBuilder";
import CoverageComparison from "./components/CoverageComparison";
import LoginPage from "./features/auth/LoginPage";
import { useQuoteDraft } from "./context/QuoteDraftContext";
import type { ComparisonDeductible } from "./types/quote";
import { ApiError, getCurrentSession } from "./services/quoteApi";
import "./App.css";

function isComparisonDeductible(value: number): value is ComparisonDeductible {
  return value === 500 || value === 1000 || value === 2000;
}

function RequireSession() {
  const [status, setStatus] = useState<"checking" | "authenticated" | "unauthenticated" | "error">("checking");
  const [error, setError] = useState("");

  const verifySession = useCallback(async () => {
    try {
      await getCurrentSession();
      setStatus("authenticated");
    } catch (sessionError) {
      if (sessionError instanceof ApiError && sessionError.status === 401) {
        setStatus("unauthenticated");
        return;
      }
      setError(sessionError instanceof Error ? sessionError.message : "Could not verify your session.");
      setStatus("error");
    }
  }, []);

  useEffect(() => {
    // The session request is an external check; its result determines the route state.
    // eslint-disable-next-line react/set-state-in-effect
    void verifySession();
  }, [verifySession]);

  function retrySessionCheck() {
    setStatus("checking");
    setError("");
    void verifySession();
  }

  if (status === "checking") return <p role="status">Checking your session…</p>;
  if (status === "unauthenticated") return <Navigate to="/login" replace />;
  if (status === "error") {
    return (
      <main>
        <p role="alert">{error}</p>
        <button type="button" onClick={retrySessionCheck}>Try again</button>
      </main>
    );
  }
  return <Outlet />;
}

function AppRoutes() {
  const navigate = useNavigate();
  const {
    setRiskDetails,
    setSelectedPackage,
    setComparisonDeductible,
    setBackendErrors,
  } = useQuoteDraft();

  return (
    <Routes>
      <Route path="/" element={<Navigate to="/login" replace />} />
      <Route path="/login" element={<LoginRoute />} />
      <Route element={<RequireSession />}>
        <Route path="/risk-details" element={<RiskDetailsForm />} />
        <Route path="/coverage" element={<CoverageBuilder />} />
        <Route path="/comparison" element={<CoverageComparison />} />
        <Route
          path="/saved-quotes"
          element={
            <SavedQuotes
              onStartNew={() => navigate("/risk-details")}
              onDuplicate={(draft) => {
                if (!isComparisonDeductible(draft.deductible)) {
                  throw new Error("The saved quote has an unsupported deductible.");
                }
                setRiskDetails(draft.inputs);
                setSelectedPackage(draft.packageCode);
                setComparisonDeductible(draft.deductible);
                setBackendErrors({});
                navigate("/risk-details");
              }}
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
