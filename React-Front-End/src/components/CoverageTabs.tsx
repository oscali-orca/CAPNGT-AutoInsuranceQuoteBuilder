import { useEffect, useState } from "react";
import { NavLink, useNavigate } from "react-router-dom";
import { getCurrentSession, signOut } from "../services/quoteApi";
import { useQuoteDraft } from "../context/QuoteDraftContext";

let cachedDisplayName: string | null = null;

export default function CoverageTabs() {
  const [displayName, setDisplayName] = useState(() => cachedDisplayName ?? "");
  const [sessionError, setSessionError] = useState("");
  const [signingOut, setSigningOut] = useState(false);
  const navigate = useNavigate();
  const {
    setRiskDetails,
    setSelectedPackage,
    setComparisonDeductible,
    setBackendErrors,
  } = useQuoteDraft();

  useEffect(() => {
    let active = true;
    getCurrentSession()
      .then((session) => {
        if (active) {
          cachedDisplayName = session.display_name;
          setDisplayName(session.display_name);
        }
      })
      .catch((error: unknown) => {
        if (active) {
          setSessionError(error instanceof Error ? error.message : "Could not load your account.");
        }
      });
    return () => {
      active = false;
    };
  }, []);

  async function handleSignOut() {
    if (signingOut) return;
    setSigningOut(true);
    setSessionError("");
    try {
      await signOut();
      cachedDisplayName = null;
      setRiskDetails(null);
      setSelectedPackage("STANDARD");
      setComparisonDeductible(1000);
      setBackendErrors({});
      navigate("/login", { replace: true });
    } catch (error) {
      setSessionError(error instanceof Error ? error.message : "Could not sign out. Please try again.");
      setSigningOut(false);
    }
  }

  return (
    <>
      <nav className="coverage-navigation" aria-label="Quote steps">
        <div className="coverage-tabs">
          <NavLink to="/risk-details">
            Risk details
          </NavLink>
          <NavLink end to="/coverage">
            Coverage builder
          </NavLink>
          <NavLink to="/comparison">
            Compare packages
          </NavLink>
          <NavLink to="/saved-quotes">
            Saved quotes
          </NavLink>
        </div>
        <div className="coverage-session">
          {displayName && <span>Signed in as <strong>{displayName}</strong></span>}
          <button className="coverage-button" disabled={signingOut} onClick={handleSignOut} type="button">
            {signingOut ? "Signing out…" : "Sign out"}
          </button>
        </div>
      </nav>
      {sessionError && <p className="coverage-session-error" role="alert">{sessionError}</p>}
    </>
  );
}
