import type {
  Breakdown, Comparison, QuoteDraft, RiskInputs, SavePayload, SavedQuoteDetail, SavedQuoteSummary,
} from '../types/savedQuotes';

// Set VITE_USE_MOCK=true in .env.local (not committed) to run without the backend.
const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true';

export class ApiError extends Error {
  status: number;
  retryable: boolean;
  fieldErrors: Record<string, string>;
  constructor(status: number, body?: { message?: string; fieldErrors?: Record<string, string> } | null) {
    super(body?.message || `Request failed (${status})`);
    this.status = status;
    this.fieldErrors = body?.fieldErrors ?? {};
    this.retryable = status === 0 || status >= 500;
  }
}

/* ---------- Real API. Paths/fields are assumptions: match them to the Spring controllers. ---------- */
async function request<T>(path: string, init: { method?: string; body?: unknown; headers?: Record<string, string> } = {}): Promise<T> {
  let res: Response;
  try {
    res = await fetch(path, {
      method: init.method ?? 'GET',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', ...init.headers },
      body: init.body ? JSON.stringify(init.body) : undefined,
    });
  } catch {
    throw new ApiError(0, { message: 'Network error. Check your connection and try again.' });
  }
  const data = res.status === 204 ? null : await res.json().catch(() => null);
  if (!res.ok) throw new ApiError(res.status, data);
  return data as T;
}

const real = {
  listQuotes: () => request<SavedQuoteSummary[]>('/api/quotes'),
  getQuote: (id: string) => request<SavedQuoteDetail>(`/api/quotes/${encodeURIComponent(id)}`),
  duplicateQuote: (id: string) => request<QuoteDraft>(`/api/quotes/${encodeURIComponent(id)}/duplicate`, { method: 'POST' }),
  compare: (inputs: RiskInputs, deductible: number) => request<Comparison>('/api/comparisons', { method: 'POST', body: { inputs, deductible } }),
  saveQuote: (payload: SavePayload, key: string) => request<SavedQuoteDetail>('/api/quotes', { method: 'POST', body: payload, headers: { 'Idempotency-Key': key } }),
};

/* ---------- Mock API (localStorage), same behaviour the backend must have ---------- */
const r2 = (n: number) => Math.round((n + Number.EPSILON) * 100) / 100; // HALF_UP for these positive values

export function factors(i: RiskInputs) {
  return {
    driver: i.driverAge <= 24 ? 1.3 : i.driverAge <= 64 ? 1 : 1.15,
    mileage: i.annualMileage <= 7500 ? 0.9 : i.annualMileage <= 15000 ? 1 : i.annualMileage <= 25000 ? 1.1 : 1.2,
    claims: 1 + 0.2 * i.priorClaims,
    vehicle: 1 + (20 - i.vehicleAge) * 0.01,
  };
}

export function price(i: RiskInputs, deductible: number): Breakdown[] {
  const k = factors(i);
  const liability = r2(600 * k.driver * k.mileage * k.claims * k.vehicle);
  const f = deductible === 500 ? 1 : deductible === 1000 ? 0.85 : 0.7;
  const collision = r2(240 * f);
  const comprehensive = r2(120 * f);
  return [
    { packageCode: 'BASIC', liability, collision: null, comprehensive: null, total: liability },
    { packageCode: 'STANDARD', liability, collision, comprehensive: null, total: r2(liability + collision) },
    { packageCode: 'ENHANCED', liability, collision, comprehensive, total: r2(liability + collision + comprehensive) },
  ];
}

const QUOTES_KEY = 'sq-mock-quotes-v2';
const KEYS_KEY = 'sq-mock-keys-v2';

function seed(): SavedQuoteDetail[] {
  const mk = (n: number, name: string, inputs: RiskInputs, deductible: number, pkg: SavedQuoteDetail['packageCode'], at: string): SavedQuoteDetail => {
    const breakdowns = price(inputs, deductible);
    return { id: `q${n}`, reference: `QT-${n}`, name, packageCode: pkg, rateVersion: 'TRAINING-1', createdAt: at, inputs, deductible, breakdowns, annualEstimate: breakdowns.find((b) => b.packageCode === pkg)!.total };
  };
  return [
    mk(2001, 'Family SUV', { driverAge: 42, vehicleAge: 6, annualMileage: 15000, priorClaims: 1 }, 1000, 'STANDARD', '2026-10-20T09:30:00-04:00'),
    mk(2002, 'Student driver', { driverAge: 21, vehicleAge: 9, annualMileage: 9000, priorClaims: 0 }, 500, 'ENHANCED', '2026-10-22T16:45:00-04:00'),
  ];
}
function readQuotes(): SavedQuoteDetail[] {
  try { const raw = localStorage.getItem(QUOTES_KEY); if (raw) return JSON.parse(raw); } catch { /* fall through */ }
  const s = seed(); localStorage.setItem(QUOTES_KEY, JSON.stringify(s)); return s;
}
const readKeys = (): Record<string, string> => { try { return JSON.parse(localStorage.getItem(KEYS_KEY) || '{}'); } catch { return {}; } };

const mock = {
  async listQuotes(): Promise<SavedQuoteSummary[]> {
    return readQuotes().map(({ inputs, deductible, breakdowns, ...s }) => s).sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  },
  async getQuote(id: string): Promise<SavedQuoteDetail> {
    const q = readQuotes().find((x) => x.id === id);
    if (!q) throw new ApiError(404, { message: 'Quote not found' });
    return q;
  },
  async duplicateQuote(id: string): Promise<QuoteDraft> {
    const q = await mock.getQuote(id);
    return { sourceReference: q.reference, inputs: { ...q.inputs }, deductible: q.deductible, packageCode: q.packageCode };
  },
  async compare(inputs: RiskInputs, deductible: number): Promise<Comparison> {
    return { rateVersion: 'TRAINING-1', breakdowns: price(inputs, deductible) };
  },
  async saveQuote(p: SavePayload, key: string): Promise<SavedQuoteDetail> {
    const keys = readKeys(); const all = readQuotes();
    const existing = keys[key] && all.find((q) => q.id === keys[key]);
    if (existing) return existing; // retry with the same key: no second record
    const n = Math.max(2000, ...all.map((q) => Number(q.reference.slice(3)))) + 1;
    const breakdowns = price(p.inputs, p.deductible);
    const saved: SavedQuoteDetail = {
      id: `q${n}`, reference: `QT-${n}`, name: p.name, packageCode: p.packageCode, rateVersion: 'TRAINING-1',
      createdAt: new Date().toISOString(), inputs: p.inputs, deductible: p.deductible, breakdowns,
      annualEstimate: breakdowns.find((b) => b.packageCode === p.packageCode)!.total,
    };
    localStorage.setItem(QUOTES_KEY, JSON.stringify([...all, saved]));
    localStorage.setItem(KEYS_KEY, JSON.stringify({ ...keys, [key]: saved.id }));
    return saved;
  },
};

export const { listQuotes, getQuote, duplicateQuote, compare, saveQuote } = USE_MOCK ? mock : real;