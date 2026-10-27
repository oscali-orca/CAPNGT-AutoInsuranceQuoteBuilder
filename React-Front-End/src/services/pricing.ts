import type {
  ComparisonDeductible,
  CoveragePackage,
  RiskDetails,
} from "../types/quote";

export const COVERAGE_PACKAGES: CoveragePackage[] = [
  "BASIC",
  "STANDARD",
  "ENHANCED",
];

export interface PricingFactor {
  name: string;
  multiplier: string;
}

export interface PackagePrice {
  base_liability: string;
  factors: PricingFactor[];
  adjusted_liability_premium: string;
  add_on_costs: {
    collision: string;
    comprehensive: string;
  };
  total_annual: string;
}

export interface ComparisonResponse {
  risk_inputs: RiskDetails;
  comparison_deductible: ComparisonDeductible;
  rate_configuration: {
    version: "TRAINING-1";
    currency: "USD";
    period: "ANNUAL";
  };
  packages: Record<CoveragePackage, PackagePrice>;
}

const BASE_LIABILITY_CENTS = 60000;
const COLLISION_BASE_CENTS = 24000;
const COMPREHENSIVE_BASE_CENTS = 12000;

const DEDUCTIBLE_MULTIPLIER_BASIS_POINTS: Record<
  ComparisonDeductible,
  number
> = {
  500: 10000,
  1000: 8500,
  2000: 7000,
};

function toDecimal(cents: number): string {
  return (cents / 100).toFixed(2);
}

function getRiskFactors(risk: RiskDetails): PricingFactor[] {
  const driver =
    risk.driverAge < 25 ? 1.3 : risk.driverAge >= 65 ? 1.15 : 1;
  const mileage = risk.annualMileage <= 7500
    ? 0.9
    : risk.annualMileage <= 15000
      ? 1
      : risk.annualMileage <= 25000
        ? 1.1
        : 1.2;
  const claims = 1 + risk.priorClaims * 0.2;
  const vehicle = 1 + (20 - risk.vehicleAge) * 0.01;

  return [
    { name: "Driver", multiplier: driver.toFixed(2) },
    { name: "Mileage", multiplier: mileage.toFixed(2) },
    { name: "Claims", multiplier: claims.toFixed(2) },
    { name: "Vehicle", multiplier: vehicle.toFixed(2) },
  ];
}

function validateRiskDetails(risk: RiskDetails): void {
  const valid =
    Number.isInteger(risk.driverAge) &&
    risk.driverAge >= 18 &&
    risk.driverAge <= 80 &&
    Number.isInteger(risk.vehicleAge) &&
    risk.vehicleAge >= 0 &&
    risk.vehicleAge <= 20 &&
    Number.isInteger(risk.annualMileage) &&
    risk.annualMileage >= 0 &&
    risk.annualMileage <= 50000 &&
    Number.isInteger(risk.priorClaims) &&
    risk.priorClaims >= 0 &&
    risk.priorClaims <= 3;

  if (!valid) {
    throw new Error("Risk details are outside the allowed pricing ranges.");
  }
}

function roundHalfUp(numerator: bigint, denominator: bigint): bigint {
  return (numerator + denominator / 2n) / denominator;
}

function multiplierToHundredths(multiplier: string): bigint {
  const [whole, fraction] = multiplier.split(".");
  if (whole === undefined || fraction === undefined || fraction.length !== 2) {
    throw new Error("Pricing factors must have two decimal places.");
  }

  return BigInt(whole) * 100n + BigInt(fraction);
}

function buildPackagePrice(
  baseLiabilityCents: number,
  factors: PricingFactor[],
  collisionCents: number,
  comprehensiveCents: number,
): PackagePrice {
  const factorProduct = factors.reduce(
    (product, factor) => product * multiplierToHundredths(factor.multiplier),
    1n,
  );
  const liabilityDenominator = 100n ** BigInt(factors.length);
  const adjustedLiabilityCents = Number(
    roundHalfUp(
      BigInt(baseLiabilityCents) * factorProduct,
      liabilityDenominator,
    ),
  );

  return {
    base_liability: toDecimal(baseLiabilityCents),
    factors,
    adjusted_liability_premium: toDecimal(adjustedLiabilityCents),
    add_on_costs: {
      collision: toDecimal(collisionCents),
      comprehensive: toDecimal(comprehensiveCents),
    },
    total_annual: toDecimal(
      adjustedLiabilityCents + collisionCents + comprehensiveCents,
    ),
  };
}

export async function calculateComparison(
  risk: RiskDetails,
  deductible: ComparisonDeductible,
  signal: AbortSignal,
): Promise<ComparisonResponse> {
  await new Promise<void>((resolve, reject) => {
    const timer = globalThis.setTimeout(resolve, 350);
    signal.addEventListener(
      "abort",
      () => {
        globalThis.clearTimeout(timer);
        reject(new DOMException("Pricing request was cancelled.", "AbortError"));
      },
      { once: true },
    );
  });

  if (signal.aborted) {
    throw new DOMException("Pricing request was cancelled.", "AbortError");
  }

  validateRiskDetails(risk);

  const factors = getRiskFactors(risk);
  const deductibleMultiplier = DEDUCTIBLE_MULTIPLIER_BASIS_POINTS[deductible];
  const collisionCents = Number(
    roundHalfUp(
      BigInt(COLLISION_BASE_CENTS) * BigInt(deductibleMultiplier),
      10000n,
    ),
  );
  const comprehensiveCents = Number(
    roundHalfUp(
      BigInt(COMPREHENSIVE_BASE_CENTS) * BigInt(deductibleMultiplier),
      10000n,
    ),
  );

  return {
    risk_inputs: { ...risk },
    comparison_deductible: deductible,
    rate_configuration: {
      version: "TRAINING-1",
      currency: "USD",
      period: "ANNUAL",
    },
    packages: {
      BASIC: buildPackagePrice(BASE_LIABILITY_CENTS, factors, 0, 0),
      STANDARD: buildPackagePrice(
        BASE_LIABILITY_CENTS,
        factors,
        collisionCents,
        0,
      ),
      ENHANCED: buildPackagePrice(
        BASE_LIABILITY_CENTS,
        factors,
        collisionCents,
        comprehensiveCents,
      ),
    },
  };
}
