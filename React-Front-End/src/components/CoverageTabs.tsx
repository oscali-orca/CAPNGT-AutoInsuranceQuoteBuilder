import {useState} from "react";
import {signOut} from "../services/quoteApi";
import {NavLink, useNavigate} from "react-router-dom";

export default function CoverageTabs() {
  const navigate = useNavigate(); 

  //tracks if logout request in progress
  const [isSigningOut, setIsSigningOut] = useState(false);
  const [logoutError, setLogoutError] = useState("");

  //ends backend session and returns user to login page
  async function handleLogout(){
    if (isSigningOut) return;

    setIsSigningOut(true);
    setLogoutError("");

    try {
      await signOut();
      navigate("/login", {replace:true});
    } catch (error) {
      setLogoutError("Failed to sign out. Please try again.");
    } finally {
      setIsSigningOut(false);
    }
  }

  return (
    <>
      <nav className="coverage-tabs" aria-label="Coverage steps">
        <NavLink end to="/risk-details">
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
        
        <button
          type = "button"
          className = "coverage-logout-button"
          onClick={handleLogout}
          disabled={isSigningOut}
        >
          {isSigningOut ? "Signing out..." : "Logout"}
        </button>
      </nav>

      {logoutError && (
        <p role="alert" className="coverage-logout-error">
          {logoutError}
        </p>
      )}
    </>
  );
}
