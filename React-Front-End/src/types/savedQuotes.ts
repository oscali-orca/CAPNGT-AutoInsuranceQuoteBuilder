export type PackageCode = 'BASIC' | 'STANDARD' | 'ENHANCED';

export interface RiskInputs {
  driverAge: number;
  vehicleAge: number;
  annualMileage: number;
  priorClaims: number;
}

export interface Breakdown {
  packageCode: PackageCode;
  liability: number;
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
}

export interface SavedQuoteDetail extends SavedQuoteSummary {
  inputs: RiskInputs;
  deductible: number;
  breakdowns: Breakdown[]; // all three, as stored
}

export interface QuoteDraft {
  sourceReference: string | null; // null for a brand-new quote
  inputs: RiskInputs;
  deductible: number;
  packageCode: PackageCode;
}

export interface Comparison {
  rateVersion: string;
  breakdowns: Breakdown[];
}

export interface SavePayload {
  name: string;
  packageCode: PackageCode;
  deductible: number;
  inputs: RiskInputs;
}