import { Link } from "react-router-dom";
import { useQuoteDraft } from "../context/QuoteDraftContext";
import { COVERAGE_PACKAGES } from "../services/pricing";
import type { ComparisonDeductible, CoveragePackage } from "../types/quote";
import { useComparisonPricing } from "../hooks/useComparisonPricing";
import CoverageTabs from "./CoverageTabs";
import "./CoverageBuilder.css";

const PACKAGE_DETAILS: Record<
  CoveragePackage,
  { name: string; inclusions: string[] }
> = {
  BASIC: { name: "Basic", inclusions: ["Liability"] },
  STANDARD: { name: "Standard", inclusions: ["Liability", "Collision"] },
  ENHANCED: {
    name: "Enhanced",
    inclusions: ["Liability", "Collision", "Comprehensive"],
  },
};

const DEDUCTIBLES: ComparisonDeductible[] = [500, 1000, 2000];
const currency = new Intl.NumberFormat("en-US", {
  style: "currency",
  currency: "USD",
});

function formatCurrency(value: string): string {
  return currency.format(Number(value));
}

export default function CoverageBuilder() {
  const {
    riskDetails,
    selectedPackage,
    setSelectedPackage,
    comparisonDeductible,
    setComparisonDeductible,
  } = useQuoteDraft();
  const { response, error, isLoading, retry } = useComparisonPricing(
    riskDetails,
    comparisonDeductible,
  );

  if (!riskDetails) {
    return (
      <main className="coverage-page coverage-empty-state">
        <h1>Coverage builder</h1>
        <p>Enter your risk details before configuring coverage.</p>
        <Link className="coverage-button coverage-button-dark" to="/risk-details">
          Enter risk details
        </Link>
      </main>
    );
  }

  function handleDeductibleChange(value: string) {
    const deductible = Number(value);
    if (DEDUCTIBLES.includes(deductible as ComparisonDeductible)) {
      setComparisonDeductible(deductible as ComparisonDeductible);
    }
  }

  return (
    <main className="coverage-page">
      <CoverageTabs />
      <div className="coverage-page-heading">
        <div>
          <h1>Configure your coverage</h1>
          <p className="coverage-subtitle">
            Step 2 · Choose a fixed package and an applicable deductible.
          </p>
        </div>
        <Link className="coverage-button" to="/risk-details">
          Edit risk details
        </Link>
      </div>

      <dl className="coverage-risk-summary">
        <div>
          <dt>Driver</dt>
          <dd>{riskDetails.driverAge} years</dd>
        </div>
        <div>
          <dt>Vehicle</dt>
          <dd>{riskDetails.vehicleAge} years old</dd>
        </div>
        <div>
          <dt>Mileage</dt>
          <dd>{riskDetails.annualMileage.toLocaleString("en-US")} miles/year</dd>
        </div>
        <div>
          <dt>Claims</dt>
          <dd>{riskDetails.priorClaims} in past 3 years</dd>
        </div>
      </dl>

      <section className="coverage-builder-options" aria-label="Select a coverage package">
        <div
          className="coverage-builder-packages"
          role="radiogroup"
          aria-label="Coverage package"
        >
          {COVERAGE_PACKAGES.map((packageId) => {
            const details = PACKAGE_DETAILS[packageId];
            const isSelected = selectedPackage === packageId;
            const price = response?.packages[packageId];

            return (
              <label
                className={
                  isSelected
                    ? "coverage-builder-card is-selected"
                    : "coverage-builder-card"
                }
                key={packageId}
              >
                <span className="coverage-builder-card-heading">
                  <input
                    checked={isSelected}
                    name="selected-coverage-package"
                    onChange={() => setSelectedPackage(packageId)}
                    type="radio"
                    value={packageId}
                  />
                  <strong>{details.name}</strong>
                </span>
                <span className="coverage-builder-card-inclusions">
                  {details.inclusions.join(" · ")}
                </span>
                {price ? (
                  <>
                    <dl className="coverage-builder-card-breakdown">
                      <div>
                        <dt>Liability</dt>
                        <dd>
                          {formatCurrency(price.adjusted_liability_premium)}
                        </dd>
                      </div>
                      <div>
                        <dt>Collision</dt>
                        <dd>{formatCurrency(price.add_on_costs.collision)}</dd>
                      </div>
                      <div>
                        <dt>Comprehensive</dt>
                        <dd>
                          {formatCurrency(price.add_on_costs.comprehensive)}
                        </dd>
                      </div>
                    </dl>
                    <span className="coverage-builder-card-price">
                      {formatCurrency(price.total_annual)}
                      <small>annual estimate</small>
                    </span>
                  </>
                ) : isLoading ? (
                  <>
                    <dl
                      className="coverage-builder-card-breakdown coverage-builder-card-skeleton"
                      aria-hidden="true"
                    >
                      {["liability", "collision", "comprehensive"].map((item) => (
                        <div key={item}>
                          <dt>
                            <span className="coverage-skeleton coverage-skeleton-label" />
                          </dt>
                          <dd>
                            <span className="coverage-skeleton coverage-skeleton-value" />
                          </dd>
                        </div>
                      ))}
                    </dl>
                    <span
                      className="coverage-builder-card-price coverage-builder-card-skeleton"
                      aria-hidden="true"
                    >
                      <span className="coverage-skeleton coverage-skeleton-total" />
                      <small>annual estimate</small>
                    </span>
                  </>
                ) : (
                  <span className="coverage-builder-card-price">
                    Price unavailable
                  </span>
                )}
              </label>
            );
          })}
        </div>

        <div className="coverage-deductible-control coverage-builder-deductible">
          <label htmlFor="coverage-deductible">
            <span>Collision / comprehensive deductible</span>
            <select
              id="coverage-deductible"
              onChange={(event) => handleDeductibleChange(event.currentTarget.value)}
              value={comparisonDeductible}
            >
              {DEDUCTIBLES.map((deductible) => (
                <option key={deductible} value={deductible}>
                  {currency.format(deductible)}
                </option>
              ))}
            </select>
          </label>
          <p>
            Applies to Standard and Enhanced. Basic is unaffected.
          </p>
        </div>

        {isLoading && (
          <div className="coverage-status" role="status" aria-live="polite">
            <span className="coverage-spinner" aria-hidden="true" />
            Calculating package prices…
          </div>
        )}
        {error && (
          <div className="coverage-status coverage-error" role="alert">
            <p>{error}</p>
            <button className="coverage-button" onClick={retry} type="button">
              Retry calculation
            </button>
          </div>
        )}
        <Link
          className="coverage-button coverage-button-dark coverage-compare-button"
          to="/comparison"
        >
          Compare all packages
        </Link>
      </section>
      <p className="coverage-footnote">
        Training estimates · Annual USD · No purchase, underwriting, or insurer
        offer
      </p>
    </main>
  );
}
