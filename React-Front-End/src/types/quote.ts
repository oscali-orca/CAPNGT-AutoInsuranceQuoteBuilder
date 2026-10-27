export interface RiskDetails{ /*defining structure (required fields) of riskdetails*/
    driverAge: number;
    vehicleAge: number;
    annualMileage: number;
    priorClaims: number;
}

export type CoveragePackage = "BASIC" | "STANDARD" | "ENHANCED";

export type ComparisonDeductible = 500 | 1000 | 2000;