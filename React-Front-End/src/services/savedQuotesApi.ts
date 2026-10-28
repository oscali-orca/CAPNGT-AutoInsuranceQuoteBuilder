import type {
  Comparison, PackageCode, QuoteDraft, RiskInputs, SavePayload, SavedQuoteDetail, SavedQuoteSummary,
} from '../types/savedQuotes';
import { ApiError, apiRequest } from './quoteApi';
import { fetchPricingComparison, type BackendPricingResponse } from './pricing';

export { ApiError } from './quoteApi';

// Only persistence is mocked. Authentication and pricing always use Java.
const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true';

type BackendInputs = (RiskInputs & { comparisonDeductible: number }) | {
  driver_age: number; vehicle_age: number; annual_mileage: number; prior_claims: number;
  comparison_deductible: number;
};

interface BackendSavedQuote {
  id: number;
  quoteReference: string;
  quoteName: string;
  selectedPackage: PackageCode;
  createdAt: string;
  snapshot: {
    inputs: BackendInputs;
    comparison: BackendPricingResponse | null;
    storedTotals?: { rateVersion: string; basicTotal: number; standardTotal: number; enhancedTotal: number } | null;
    warning?: string | null;
  };
}

interface BackendDraft {
  quoteName: string;
  selectedPackage: PackageCode;
  pricingInputs: BackendInputs;
  comparison: BackendPricingResponse;
}

function readInputs(inputs: BackendInputs): RiskInputs {
  return 'driver_age' in inputs ? {
    driverAge: inputs.driver_age, vehicleAge: inputs.vehicle_age,
    annualMileage: inputs.annual_mileage, priorClaims: inputs.prior_claims,
  } : {
    driverAge: inputs.driverAge, vehicleAge: inputs.vehicleAge,
    annualMileage: inputs.annualMileage, priorClaims: inputs.priorClaims,
  };
}

function toComparison(result: BackendPricingResponse): Comparison {
  return {
    rateVersion: result.rateVersion,
    riskFactors: result.riskFactors,
    breakdowns: [result.basic, result.standard, result.enhanced].map((p) => ({
      packageCode: p.packageType,
      liability: p.liability,
      collision: p.packageType === 'BASIC' ? null : p.collision,
      comprehensive: p.packageType === 'ENHANCED' ? p.comprehensive : null,
      total: p.annualTotal,
    })),
  };
}

function toSavedQuote(q: BackendSavedQuote): SavedQuoteDetail {
  const result = q.snapshot.comparison;
  const comparison = result ? toComparison(result) : null;
  const totals = q.snapshot.storedTotals;
  if (!comparison && !totals) {
    throw new ApiError(502, { message: 'This saved quote has no stored prices. Historical prices were not recalculated.' });
  }
  const breakdowns = comparison?.breakdowns ?? [
    { packageCode: 'BASIC' as const, liability: null, collision: null, comprehensive: null, total: totals!.basicTotal },
    { packageCode: 'STANDARD' as const, liability: null, collision: null, comprehensive: null, total: totals!.standardTotal },
    { packageCode: 'ENHANCED' as const, liability: null, collision: null, comprehensive: null, total: totals!.enhancedTotal },
  ];
  const inputs = q.snapshot.inputs;
  return {
    id: String(q.id), reference: q.quoteReference, name: q.quoteName,
    packageCode: q.selectedPackage, createdAt: q.createdAt,
    rateVersion: comparison?.rateVersion ?? totals!.rateVersion, inputs: readInputs(inputs),
    deductible: result ? result.comparisonDeductible
      : 'comparison_deductible' in inputs ? inputs.comparison_deductible : inputs.comparisonDeductible,
    breakdowns, riskFactors: comparison?.riskFactors,
    annualEstimate: breakdowns.find((b) => b.packageCode === q.selectedPackage)!.total,
    historyWarning: q.snapshot.warning ?? undefined,
  };
}

function saveBody(p: SavePayload, key: string) {
  return {
    quoteName: p.name.trim(), saveRequestKey: key, selectedPackage: p.packageCode,
    pricingInputs: {
      driverAge: p.inputs.driverAge, vehicleAge: p.inputs.vehicleAge,
      annualMileage: p.inputs.annualMileage, priorClaims: p.inputs.priorClaims,
      comparisonDeductible: p.deductible,
    },
  };
}

const real = {
  async listQuotes(): Promise<SavedQuoteSummary[]> {
    return (await apiRequest<BackendSavedQuote[]>('/api/quotes')).map(toSavedQuote);
  },
  async getQuote(id: string): Promise<SavedQuoteDetail> {
    return toSavedQuote(await apiRequest<BackendSavedQuote>(`/api/quotes/${encodeURIComponent(id)}`));
  },
  async duplicateQuote(id: string, sourceReference: string | null = null): Promise<QuoteDraft> {
    const draft = await apiRequest<BackendDraft>(`/api/quotes/${encodeURIComponent(id)}/duplicate`, { method: 'POST' });
    return {
      sourceReference, name: draft.quoteName, packageCode: draft.selectedPackage,
      inputs: readInputs(draft.pricingInputs), deductible: draft.comparison.comparisonDeductible,
      comparison: toComparison(draft.comparison),
    };
  },
  async compare(inputs: RiskInputs, deductible: number): Promise<Comparison> {
    return toComparison(await fetchPricingComparison(inputs, deductible));
  },
  async saveQuote(payload: SavePayload, key: string): Promise<SavedQuoteDetail> {
    return toSavedQuote(await apiRequest<BackendSavedQuote>('/api/quotes', {
      method: 'POST', body: saveBody(payload, key),
    }));
  },
};

// Browser-only storage for UI work; never used by the real API branch.
const QUOTES_KEY = 'sq-mock-quotes-v2';
const KEYS_KEY = 'sq-mock-keys-v2';
function readQuotes(): SavedQuoteDetail[] {
  try { return JSON.parse(localStorage.getItem(QUOTES_KEY) || '[]'); } catch { return []; }
}
function readKeys(): Record<string, string> {
  try { return JSON.parse(localStorage.getItem(KEYS_KEY) || '{}'); } catch { return {}; }
}

const mock = {
  async listQuotes(): Promise<SavedQuoteSummary[]> {
    return readQuotes().sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  },
  async getQuote(id: string): Promise<SavedQuoteDetail> {
    const q = readQuotes().find((x) => x.id === id);
    if (!q) throw new ApiError(404, { message: 'Quote not found' });
    return q;
  },
  async duplicateQuote(id: string, sourceReference: string | null = null): Promise<QuoteDraft> {
    const q = await mock.getQuote(id);
    return {
      sourceReference: sourceReference ?? q.reference, name: q.name.slice(0, 93) + ' (copy)',
      inputs: { ...q.inputs }, deductible: q.deductible, packageCode: q.packageCode,
      comparison: await real.compare(q.inputs, q.deductible),
    };
  },
  compare: real.compare,
  async saveQuote(p: SavePayload, key: string): Promise<SavedQuoteDetail> {
    const keys = readKeys();
    const existing = keys[key] && readQuotes().find((q) => q.id === keys[key]);
    if (existing) {
      if (JSON.stringify(saveBody(existing, key)) !== JSON.stringify(saveBody(p, key))) {
        throw new ApiError(409, { message: 'Save request key was already used with a different payload' });
      }
      return existing;
    }
    const comparison = await real.compare(p.inputs, p.deductible);
    const all = readQuotes();
    const saved: SavedQuoteDetail = {
      id: crypto.randomUUID(), reference: `MOCK-${all.length + 1}`, name: p.name.trim(),
      packageCode: p.packageCode, createdAt: new Date().toISOString(), inputs: { ...p.inputs },
      deductible: p.deductible, rateVersion: comparison.rateVersion,
      breakdowns: comparison.breakdowns, riskFactors: comparison.riskFactors,
      annualEstimate: comparison.breakdowns.find((b) => b.packageCode === p.packageCode)!.total,
    };
    localStorage.setItem(QUOTES_KEY, JSON.stringify([...all, saved]));
    localStorage.setItem(KEYS_KEY, JSON.stringify({ ...keys, [key]: saved.id }));
    return saved;
  },
};

export const { listQuotes, getQuote, duplicateQuote, compare, saveQuote } = USE_MOCK ? mock : real;
