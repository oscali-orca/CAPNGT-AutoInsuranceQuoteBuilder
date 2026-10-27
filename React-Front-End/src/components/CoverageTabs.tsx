import { NavLink } from "react-router-dom";

export default function CoverageTabs() {
  return (
    <nav className="coverage-tabs" aria-label="Coverage steps">
      <NavLink end to="/coverage">
        Coverage builder
      </NavLink>
      <NavLink to="/comparison">
        Compare packages
      </NavLink>
      <NavLink to="/saved-quotes">
        Saved quotes
      </NavLink>
    </nav>
  );
}
