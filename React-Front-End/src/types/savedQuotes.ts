export type PackageCode = 'BASIC' | 'STANDARD' | 'ENHANCED';

export interface RiskFactors {
  driverFactor: number;
  mileageFactor: number;
  claimsFactor: number;
  vehicleFactor: number;
}

export interface RiskInputs {
  driverAge: number;
  vehicleAge: number;
  annualMileage: number;
  priorClaims: number;
}

export interface Breakdown {
  packageCode: PackageCode;
  liability: number | null;
  collision: number | null; // null when the package has no collision cover
  comprehensive: number | null;
  total: number;
}

export interface SavedQuoteSummary {
  id: string;
  reference: string; // e.g. QT-1001
  name: string;
  packageCode: PackageCode;
  annualEstimate: number;
  rateVersion: string; // e.g. TRAINING-1
  createdAt: string; // ISO
  historyWarning?: string;
}

export interface SavedQuoteDetail extends SavedQuoteSummary {
  inputs: RiskInputs;
  deductible: number;
  breakdowns: Breakdown[]; // all three, as stored
  riskFactors?: RiskFactors;
}

export interface QuoteDraft {
  sourceReference: string | null; // null for a brand-new quote
  inputs: RiskInputs;
  deductible: number;
  packageCode: PackageCode;
  name?: string;
  comparison?: Comparison;
}

export interface Comparison {
  rateVersion: string;
  breakdowns: Breakdown[];
  riskFactors: RiskFactors;
}

export interface SavePayload {
  name: string;
  packageCode: PackageCode;
  deductible: number;
  inputs: RiskInputs;
}
