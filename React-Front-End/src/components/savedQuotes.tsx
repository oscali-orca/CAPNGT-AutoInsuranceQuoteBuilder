import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import './SavedQuotes.css';
import { ApiError, compare, duplicateQuote, factors, getQuote, listQuotes, saveQuote } from '../services/savedQuotesApi';
import type { Breakdown, PackageCode, QuoteDraft, RiskInputs, SavedQuoteDetail } from '../types/savedQuotes';

const money = (n: number) => new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(n);
const when = (iso: string) => new Date(iso).toLocaleString('en-US', { dateStyle: 'medium', timeStyle: 'short' });
const n0 = (n: number) => n.toLocaleString('en-US');
const PACKAGES: Record<PackageCode, string> = { BASIC: 'Basic', STANDARD: 'Standard', ENHANCED: 'Enhanced' };
const BLANK: QuoteDraft = { sourceReference: null, inputs: { driverAge: 30, vehicleAge: 3, annualMileage: 12000, priorClaims: 0 }, deductible: 1000, packageCode: 'STANDARD' };

const FIELDS: { key: keyof RiskInputs; label: string; min: number; max: number; hint: string }[] = [
  { key: 'driverAge', label: 'Driver age', min: 18, max: 80, hint: '18 to 80 years' },
  { key: 'vehicleAge', label: 'Vehicle age', min: 0, max: 20, hint: '0 to 20 years' },
  { key: 'annualMileage', label: 'Annual mileage', min: 0, max: 50000, hint: '0 to 50,000 miles per year' },
  { key: 'priorClaims', label: 'Claims in the past 3 years', min: 0, max: 3, hint: '0 to 3 prior claims' },
];

function describe(e: unknown): string {
  if (!(e instanceof ApiError)) return 'Something went wrong. Try again.';
  if (e.status === 401) return 'Your session has ended. Log in again to continue.';
  if (e.status === 403 || e.status === 404) return 'This quote is not available. It may belong to another customer or no longer exist.';
  if (e.status === 409) return 'This save conflicts with an earlier one. Review the quote and save again.';
  return e.message;
}

function ErrorBanner({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  if (!error) return null;
  const retry = onRetry && !(error instanceof ApiError && !error.retryable);
  return <div className="alert" role="alert">{describe(error)} {retry && <button className="link" onClick={onRetry}>Try again</button>}</div>;
}

function useLoad<T>(fn: () => Promise<T>, deps: unknown[]) {
  const [state, set] = useState<{ loading: boolean; data: T | null; error: unknown }>({ loading: true, data: null, error: null });
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const run = useCallback(() => {
    set((s) => ({ ...s, loading: true, error: null }));
    fn().then((data) => set({ loading: false, data, error: null }), (error) => set({ loading: false, data: null, error }));
  }, deps);
  useEffect(run, [run]);
  return [state, run] as const;
}

/** One idempotency key per distinct payload: a retry reuses it, an edit gets a new one. */
function useSaveKey(payload: unknown) {
  const ref = useRef({ sig: '', key: '' });
  const sig = JSON.stringify(payload);
  if (ref.current.sig !== sig) ref.current = { sig, key: crypto.randomUUID() };
  return ref.current.key;
}

export function SaveQuoteForm(props: { packageCode: PackageCode; deductible: number; inputs: RiskInputs; disabled?: boolean; onSaved: (q: SavedQuoteDetail) => void }) {
  const [name, setName] = useState('');
  const [touched, setTouched] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const trimmed = name.trim();
  const payload = { name: trimmed, packageCode: props.packageCode, deductible: props.deductible, inputs: props.inputs };
  const key = useSaveKey(payload);
  const nameError = touched && !trimmed ? 'Enter a name for this quote.' : error instanceof ApiError ? error.fieldErrors.name : undefined;

  async function submit(e?: FormEvent) {
    e?.preventDefault();
    setTouched(true);
    if (!trimmed || saving) return;
    setSaving(true); setError(null);
    try { props.onSaved(await saveQuote(payload, key)); } catch (err) { setError(err); setSaving(false); }
  }
  return (
    <form onSubmit={submit} noValidate style={{ maxWidth: 420 }}>
      <label className="field">Quote name
        <input value={name} maxLength={80} disabled={saving} aria-invalid={!!nameError} aria-describedby="sq-name-err"
               onChange={(e) => setName(e.target.value)} onBlur={() => setTouched(true)} />
        <span id="sq-name-err" className="err">{nameError}</span>
      </label>
      <ErrorBanner error={error} onRetry={() => submit()} />
      <button className="primary" type="submit" disabled={saving || props.disabled}>{saving ? 'Saving…' : 'Save as new quote'}</button>
    </form>
  );
}

function Strip({ inputs }: { inputs: RiskInputs }) {
  return (
    <div className="strip">
      <span>Driver<b>{inputs.driverAge} years</b></span>
      <span>Vehicle<b>{inputs.vehicleAge} years old</b></span>
      <span>Mileage<b>{n0(inputs.annualMileage)} miles/year</b></span>
      <span>Claims<b>{inputs.priorClaims} in past 3 years</b></span>
    </div>
  );
}

function ComparisonTable({ breakdowns, deductible, selected, onSelect }: { breakdowns: Breakdown[]; deductible: number; selected: PackageCode; onSelect?: (c: PackageCode) => void }) {
  const cell = (b: Breakdown, content: React.ReactNode, cls = '') => <td key={b.packageCode} className={`${b.packageCode === selected ? 'sel ' : ''}${cls}`}>{content}</td>;
  return (
    <div className="wrap">
      <table className="cmp">
        <thead><tr><th>Annual USD</th>{breakdowns.map((b) => <th key={b.packageCode}>{PACKAGES[b.packageCode]}{b.packageCode === selected && ' · selected'}</th>)}</tr></thead>
        <tbody>
          <tr><th scope="row">Coverage</th>{breakdowns.map((b) => cell(b, <>Liability{b.collision != null && <><br />Collision</>}{b.comprehensive != null && <><br />Comprehensive</>}</>))}</tr>
          <tr><th scope="row">Deductible</th>{breakdowns.map((b) => cell(b, b.collision == null ? 'Not applicable' : money(deductible)))}</tr>
          <tr><th scope="row">Adjusted liability</th>{breakdowns.map((b) => cell(b, money(b.liability)))}</tr>
          <tr><th scope="row">Collision</th>{breakdowns.map((b) => cell(b, money(b.collision ?? 0)))}</tr>
          <tr><th scope="row">Comprehensive</th>{breakdowns.map((b) => cell(b, money(b.comprehensive ?? 0)))}</tr>
          <tr className="total"><th scope="row">Annual estimate</th>{breakdowns.map((b) => cell(b, money(b.total)))}</tr>
          {onSelect && (
            <tr><th scope="row">Choose</th>{breakdowns.map((b) => cell(b,
              <button className={b.packageCode === selected ? 'primary' : ''} aria-pressed={b.packageCode === selected} onClick={() => onSelect(b.packageCode)}>
                {b.packageCode === selected ? 'Selected' : `Select ${PACKAGES[b.packageCode]}`}
              </button>))}</tr>
          )}
        </tbody>
      </table>
    </div>
  );
}

function Formula({ inputs }: { inputs: RiskInputs }) {
  const k = factors(inputs);
  const f = (x: number) => x.toFixed(2);
  return <p className="formula">Base liability $600.00 × driver {f(k.driver)} × mileage {f(k.mileage)} × claims {f(k.claims)} × vehicle {f(k.vehicle)}.</p>;
}

function List({ onOpen, onStartNew }: { onOpen: (id: string) => void; onStartNew: () => void }) {
  const [{ loading, data, error }, reload] = useLoad(listQuotes, []);
  return (
    <>
      <div className="head">
        <div><h2>My saved quotes</h2><p className="sub">Stored training estimates remain unchanged when you edit a new draft.</p></div>
        <button className="primary" onClick={onStartNew}>Start a new quote</button>
      </div>
      {loading && <p role="status">Loading saved quotes…</p>}
      <ErrorBanner error={error} onRetry={reload} />
      {data && data.length === 0 && <p>You have no saved quotes yet. Choose Start a new quote, compare packages, then save one.</p>}
      {data && data.length > 0 && (
        <table>
          <thead><tr><th>Reference / name</th><th>Package</th><th>Annual estimate</th><th className="hide-sm">Created</th><th>Action</th></tr></thead>
          <tbody>
            {data.map((q) => (
              <tr key={q.id}>
                <td>{q.reference}<small>{q.name}</small></td>
                <td>{PACKAGES[q.packageCode]}</td>
                <td>{money(q.annualEstimate)}<small>{q.rateVersion}</small></td>
                <td className="hide-sm">{when(q.createdAt)}</td>
                <td><button onClick={() => onOpen(q.id)} aria-label={`View ${q.reference}`}>View</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </>
  );
}

// Shows stored values only. It never calls the pricing endpoint.
function Detail({ id, onBack, onDuplicate }: { id: string; onBack: () => void; onDuplicate: (d: QuoteDraft) => void }) {
  const [{ loading, data: q, error }, reload] = useLoad(() => getQuote(id), [id]);
  const [busy, setBusy] = useState(false);
  const [dupError, setDupError] = useState<unknown>(null);
  async function duplicate() {
    setBusy(true); setDupError(null);
    try { onDuplicate(await duplicateQuote(id)); } catch (e) { setDupError(e); setBusy(false); }
  }
  if (loading) return <p role="status">Loading quote…</p>;
  if (error || !q) return <><ErrorBanner error={error} onRetry={reload} /><button onClick={onBack}>Back to saved quotes</button></>;
  return (
    <>
      <div className="head">
        <div><h2>{q.reference} · {q.name}</h2><p className="sub">Saved {when(q.createdAt)}</p></div>
        <button className="primary" onClick={duplicate} disabled={busy}>{busy ? 'Copying…' : 'Duplicate & edit'}</button>
      </div>
      <ErrorBanner error={dupError} onRetry={duplicate} />
      <div className="note">Saved snapshot · {q.rateVersion} · {PACKAGES[q.packageCode]} selected · Original prices are preserved.</div>
      <Strip inputs={q.inputs} />
      <ComparisonTable breakdowns={q.breakdowns} deductible={q.deductible} selected={q.packageCode} />
      <Formula inputs={q.inputs} />
      <div className="actions"><button onClick={onBack}>Back to saved quotes</button></div>
    </>
  );
}

type Priced = { rateVersion: string; breakdowns: Breakdown[]; inputs: RiskInputs; deductible: number };

// The draft lives only in React state. The original snapshot is never sent back.
function Draft({ draft, onCancel, onSaved }: { draft: QuoteDraft; onCancel: () => void; onSaved: (q: SavedQuoteDetail) => void }) {
  const [text, setText] = useState(() => Object.fromEntries(Object.entries(draft.inputs).map(([k, v]) => [k, String(v)])) as Record<keyof RiskInputs, string>);
  const [deductible, setDeductible] = useState(draft.deductible);
  const [pkg, setPkg] = useState<PackageCode>(draft.packageCode);
  const [priced, setPriced] = useState<Priced | null>(null);
  const [step, setStep] = useState<'edit' | 'compare'>('edit');
  const [pricing, setPricing] = useState(false);
  const [errors, setErrors] = useState<Partial<Record<keyof RiskInputs, string>>>({});
  const [error, setError] = useState<unknown>(null);
  const ref = draft.sourceReference;

  async function next() {
    const errs: typeof errors = {}; const inputs = {} as RiskInputs;
    for (const f of FIELDS) {
      const v = Number(text[f.key]);
      if (text[f.key].trim() === '' || !Number.isInteger(v) || v < f.min || v > f.max) errs[f.key] = `Enter a whole number: ${f.hint}.`;
      else inputs[f.key] = v;
    }
    setErrors(errs);
    if (Object.keys(errs).length) return;
    setPricing(true); setError(null);
    try { setPriced({ ...(await compare(inputs, deductible)), inputs, deductible }); setStep('compare'); }
    catch (e) { setError(e); } finally { setPricing(false); }
  }

  if (step === 'compare' && priced) {
    return (
      <>
        <h2>Compare packages</h2>
        <p className="sub">Step 2 · Choose a package and save it as a new quote.</p>
        <div className="note" style={{ marginTop: 20 }}>Prices were recalculated at current rates ({priced.rateVersion}){ref ? ` and may differ from ${ref}. The original snapshot is unchanged.` : '.'}</div>
        <Strip inputs={priced.inputs} />
        <ComparisonTable breakdowns={priced.breakdowns} deductible={priced.deductible} selected={pkg} onSelect={setPkg} />
        <Formula inputs={priced.inputs} />
        <h3>Save as a new quote</h3>
        <SaveQuoteForm packageCode={pkg} deductible={priced.deductible} inputs={priced.inputs} onSaved={onSaved} />
        <div className="actions"><button onClick={() => setStep('edit')}>Back to edit details</button><button onClick={onCancel}>Discard draft</button></div>
      </>
    );
  }
  return (
    <>
      <h2>Vehicle & driver details</h2>
      <p className="sub">Step 1 · Enter the inputs for your training estimate.</p>
      <div className="note" style={{ marginTop: 20 }}>{ref ? `Editing a new draft copied from ${ref}. The original snapshot is unchanged.` : 'Starting a new draft.'}</div>
      <div className="layout">
        <div>
          <div className="fields">
            {FIELDS.map((f) => (
              <label className="field" key={f.key}>{f.label}
                <input inputMode="numeric" value={text[f.key]} aria-invalid={!!errors[f.key]} onChange={(e) => setText({ ...text, [f.key]: e.target.value })} />
                <span className={errors[f.key] ? 'err' : 'hint'}>{errors[f.key] ?? f.hint}</span>
              </label>
            ))}
            <label className="field">Deductible
              <select value={deductible} onChange={(e) => setDeductible(Number(e.target.value))}>
                {[500, 1000, 2000].map((d) => <option key={d} value={d}>{money(d)}</option>)}
              </select>
              <span className="hint">Applies to collision and comprehensive</span>
            </label>
          </div>
          <ErrorBanner error={error} onRetry={next} />
          <div className="actions">
            <button className="primary" onClick={next} disabled={pricing}>{pricing ? 'Calculating…' : 'Continue to comparison'}</button>
            <button onClick={onCancel}>Discard draft</button>
          </div>
        </div>
        <aside className="aside">
          <h3>Build your estimate</h3>
          <p>Compare three packages at current rates, then save a quote to revisit later.</p>
          <p className="muted">All prices are fictional annual USD estimates. No policy purchase or payment is part of this exercise.</p>
        </aside>
      </div>
    </>
  );
}

type View = { name: 'list' } | { name: 'detail'; id: string } | { name: 'draft'; draft: QuoteDraft };

export default function SavedQuotes({ onStartNew }: { onStartNew?: () => void }) {
  const [view, setView] = useState<View>({ name: 'list' });
  return (
    <div className="sq">
      {view.name === 'list' && <List onOpen={(id) => setView({ name: 'detail', id })} onStartNew={() => (onStartNew ? onStartNew() : setView({ name: 'draft', draft: BLANK }))} />}
      {view.name === 'detail' && <Detail id={view.id} onBack={() => setView({ name: 'list' })} onDuplicate={(draft) => setView({ name: 'draft', draft })} />}
      {view.name === 'draft' && <Draft draft={view.draft} onCancel={() => setView({ name: 'list' })} onSaved={(q) => setView({ name: 'detail', id: q.id })} />}
    </div>
  );
}