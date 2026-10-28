import { useRef, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { useQuoteDraft } from "../context/QuoteDraftContext";
import {
  COVERAGE_PACKAGES,
  type ComparisonResponse,
} from "../services/pricing";
import type { ComparisonDeductible, CoveragePackage } from "../types/quote";
import { useComparisonPricing } from "../hooks/useComparisonPricing";
import { saveQuote } from "../services/savedQuotesApi";
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

function PriceBreakdown({
  response,
  packageId,
}: {
  response: ComparisonResponse;
  packageId: CoveragePackage;
}) {
  const price = response.packages[packageId];
  const deductibleFactor = response.deductible_factor;

  return (
    <section
      className="coverage-breakdown"
      aria-labelledby="coverage-comparison-breakdown-title"
    >
      <h2 id="coverage-comparison-breakdown-title">
        {PACKAGE_DETAILS[packageId].name} price breakdown
      </h2>
      <dl className="coverage-price-rows">
        <div>
          <dt>Adjusted liability</dt>
          <dd>{formatCurrency(price.adjusted_liability_premium)}</dd>
        </div>
        <div>
          <dt>Collision</dt>
          <dd>{formatCurrency(price.add_on_costs.collision)}</dd>
        </div>
        <div>
          <dt>Comprehensive</dt>
          <dd>{formatCurrency(price.add_on_costs.comprehensive)}</dd>
        </div>
        <div className="coverage-annual-total">
          <dt>Annual estimate</dt>
          <dd>{formatCurrency(price.total_annual)}</dd>
        </div>
      </dl>
      <p className="coverage-breakdown-note">
        Risk multipliers: {" "}
        {price.factors
          .map((factor) => `${factor.name.toLowerCase()} ${factor.multiplier}`)
          .join(" × ")}
        .
      </p>
      <p className="coverage-breakdown-note">
        Deductible multiplier {deductibleFactor} applies to collision and comprehensive
        when included. Premiums are calculated by the server and rounded HALF_UP to cents. Rate version{" "}
        {response.rate_configuration.version}.
      </p>
    </section>
  );
}

export default function CoverageComparison() {
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
  const [quoteName, setQuoteName] = useState("");
  const [saveMessage, setSaveMessage] = useState<{
    type: "success" | "error";
    text: string;
  } | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const saveAttempt = useRef<{ signature: string; key: string } | null>(null);

  if (!riskDetails) {
    return (
      <main className="coverage-page coverage-empty-state">
        <h1>Compare annual estimates</h1>
        <p>Enter your risk details before comparing packages.</p>
        <Link className="coverage-button coverage-button-dark" to="/risk-details">
          Enter risk details
        </Link>
      </main>
    );
  }

  async function handleSaveQuote(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const name = quoteName.trim();
    if (!name || !response || isSaving) return;

    setIsSaving(true);
    const payload = {
      name,
      packageCode: selectedPackage,
      inputs: response.risk_inputs,
      deductible: response.comparison_deductible,
    };
    const signature = JSON.stringify(payload);
    if (saveAttempt.current?.signature !== signature) {
      saveAttempt.current = { signature, key: crypto.randomUUID() };
    }
    try {
      const saved = await saveQuote(payload, saveAttempt.current.key);
      setSaveMessage({
        type: "success",
        text: `“${saved.name}” was saved as ${saved.reference}.`,
      });
    } catch (saveError) {
      setSaveMessage({
        type: "error",
        text:
          saveError instanceof Error
            ? `Could not save quote: ${saveError.message}`
            : "Could not save quote. Please try again.",
      });
    } finally {
      setIsSaving(false);
    }
  }

  function handleDeductibleChange(value: string) {
    const deductible = Number(value);
    if (DEDUCTIBLES.includes(deductible as ComparisonDeductible)) {
      setComparisonDeductible(deductible as ComparisonDeductible);
      setSaveMessage(null);
    }
  }

  return (
    <main className="coverage-page coverage-comparison">
      <CoverageTabs />
      <div className="coverage-page-heading">
        <div>
          <h1>Compare annual estimates</h1>
          <p className="coverage-subtitle">
            Step 3 · All packages use the same risk inputs and training rate
            version.
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

      <section className="coverage-options">
        <div className="coverage-deductible-control">
          <label htmlFor="comparison-deductible">
            <span>Collision / comprehensive deductible</span>
            <select
              id="comparison-deductible"
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
          <p>Shared across Standard and Enhanced. Basic is unaffected.</p>
        </div>

        {isLoading && (
          <div className="coverage-status" role="status" aria-live="polite">
            <span className="coverage-spinner" aria-hidden="true" />
            Calculating all three estimates…
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

        {response && (
          <>
            <div
              className="coverage-package-grid"
              role="radiogroup"
              aria-label="Selected package for quote"
            >
              {COVERAGE_PACKAGES.map((packageId) => {
                const details = PACKAGE_DETAILS[packageId];
                const isSelected = selectedPackage === packageId;
                const price = response.packages[packageId];

                return (
                  <label
                    className={
                      isSelected
                        ? "coverage-package-card is-selected"
                        : "coverage-package-card"
                    }
                    key={packageId}
                  >
                    <span className="coverage-package-select">
                      <input
                        checked={isSelected}
                        name="selected-coverage-package"
                        onChange={() => {
                          setSelectedPackage(packageId);
                          setSaveMessage(null);
                        }}
                        type="radio"
                        value={packageId}
                      />
                      <span>{details.name}</span>
                    </span>
                    <span className="coverage-comparison-inclusions">
                      {details.inclusions.join(" · ")}
                    </span>
                    <p className="coverage-package-price">
                      <strong>{formatCurrency(price.total_annual)}</strong>
                      <span>annual USD</span>
                    </p>
                    <span className="coverage-comparison-deductible">
                      {packageId === "BASIC" ? (
                        "No deductible"
                      ) : (
                        <>
                          Deductible{" "}
                          <strong>
                            {currency.format(comparisonDeductible)}
                          </strong>
                        </>
                      )}
                    </span>
                  </label>
                );
              })}
            </div>

            <PriceBreakdown packageId={selectedPackage} response={response} />

            <section className="coverage-save" aria-labelledby="coverage-save-title">
              <h2 id="coverage-save-title">Save selected quote</h2>
              <form className="coverage-save-form" onSubmit={handleSaveQuote}>
                <label htmlFor="coverage-quote-name">Quote name</label>
                <div className="coverage-save-controls">
                  <input
                    id="coverage-quote-name"
                    maxLength={100}
                    onChange={(event) => {
                      setQuoteName(event.currentTarget.value);
                      setSaveMessage(null);
                    }}
                    placeholder="e.g. My commute option"
                    required
                    type="text"
                    value={quoteName}
                  />
                  <button
                    className="coverage-button coverage-button-dark"
                    disabled={!quoteName.trim() || isSaving}
                    type="submit"
                  >
                    {isSaving
                      ? "Saving…"
                      : `Save ${PACKAGE_DETAILS[selectedPackage].name} quote`}
                  </button>
                </div>
                <p className="coverage-save-note">
                  Saves the selected package, inputs, all three prices, and rate
                  version as a saved snapshot.
                </p>
                {saveMessage && (
                  <>
                    <p
                      className={`coverage-save-message is-${saveMessage.type}`}
                      role={saveMessage.type === "error" ? "alert" : "status"}
                    >
                      {saveMessage.text}
                    </p>
                    {saveMessage.type === "success" && (
                      <Link className="coverage-button" to="/saved-quotes">
                        View saved quotes
                      </Link>
                    )}
                  </>
                )}
              </form>
            </section>
          </>
        )}
        <p className="coverage-footnote">
          Training estimates · Annual USD · No purchase, underwriting, or insurer
          offer
        </p>
      </section>
    </main>
  );
}
