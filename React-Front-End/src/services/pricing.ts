import type { ComparisonDeductible, CoveragePackage, RiskDetails } from "../types/quote";
import type { RiskFactors } from "../types/savedQuotes";
import { apiRequest } from "./quoteApi";

export const COVERAGE_PACKAGES: CoveragePackage[] = ["BASIC", "STANDARD", "ENHANCED"];

export interface PricingFactor {
  name: string;
  multiplier: string;
}

export interface PackagePrice {
  factors: PricingFactor[];
  adjusted_liability_premium: string;
  add_on_costs: { collision: string; comprehensive: string };
  total_annual: string;
}

export interface ComparisonResponse {
  risk_inputs: RiskDetails;
  comparison_deductible: ComparisonDeductible;
  deductible_factor: string;
  rate_configuration: { version: string; currency: string; period: string };
  packages: Record<CoveragePackage, PackagePrice>;
}

export interface BackendPackage {
  packageType: CoveragePackage;
  liability: number;
  collision: number;
  comprehensive: number;
  deductibleApplicable: boolean;
  deductible: number | null;
  annualTotal: number;
}

export interface BackendPricingResponse {
  rateVersion: string;
  currency: string;
  period: string;
  riskFactors: RiskFactors;
  comparisonDeductible: ComparisonDeductible;
  deductibleFactor: number;
  basic: BackendPackage;
  standard: BackendPackage;
  enhanced: BackendPackage;
}

export function fetchPricingComparison(risk: RiskDetails, deductible: number, signal?: AbortSignal) {
  return apiRequest<BackendPricingResponse>("/api/pricing/compare", {
    method: "POST",
    body: { ...risk, comparisonDeductible: deductible },
    signal,
  });
}

export async function calculateComparison(
  risk: RiskDetails,
  deductible: ComparisonDeductible,
  signal: AbortSignal,
): Promise<ComparisonResponse> {
  const result = await fetchPricingComparison(risk, deductible, signal);
  const factors: PricingFactor[] = [
    { name: "Driver", multiplier: String(result.riskFactors.driverFactor) },
    { name: "Mileage", multiplier: String(result.riskFactors.mileageFactor) },
    { name: "Claims", multiplier: String(result.riskFactors.claimsFactor) },
    { name: "Vehicle", multiplier: String(result.riskFactors.vehicleFactor) },
  ];
  // Format backend amounts for the existing UI; do not recalculate premiums.
  const displayPackage = (price: BackendPackage): PackagePrice => ({
    factors,
    adjusted_liability_premium: price.liability.toFixed(2),
    add_on_costs: {
      collision: price.collision.toFixed(2),
      comprehensive: price.comprehensive.toFixed(2),
    },
    total_annual: price.annualTotal.toFixed(2),
  });
  return {
    risk_inputs: { ...risk },
    comparison_deductible: result.comparisonDeductible,
    deductible_factor: String(result.deductibleFactor),
    rate_configuration: { version: result.rateVersion, currency: result.currency, period: result.period },
    packages: {
      BASIC: displayPackage(result.basic),
      STANDARD: displayPackage(result.standard),
      ENHANCED: displayPackage(result.enhanced),
    },
  };
}
