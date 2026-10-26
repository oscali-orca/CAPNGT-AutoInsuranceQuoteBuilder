# Data dictionary · Training quote exercise

All money is `NUMERIC(12,2)` in columns and exact decimal numbers in JSON.
Java reads every money value as `BigDecimal`. Never `double` or `float`.

## customer

| Column | Type | Rules |
|--------|------|-------|
| id | BIGSERIAL | Primary key |
| username | VARCHAR(50) | Required, unique, not blank |
| display_name | VARCHAR(100) | Required |
| password_hash | VARCHAR(100) | Required, BCrypt |
| created_at | TIMESTAMPTZ | Defaults to now |

## rate_configuration

| Column | Type | Rules |
|--------|------|-------|
| id | BIGSERIAL | Primary key |
| version_code | VARCHAR(30) | Required, unique, e.g. `TRAINING-1` |
| description | VARCHAR(255) | Optional |
| currency | CHAR(3) | Defaults to `USD` |
| is_active | BOOLEAN | At most one row can be `true` |
| config | JSONB | Rate tables for this version. **Shape defined by T-08** |
| created_at | TIMESTAMPTZ | Defaults to now |

## saved_quote

Immutable: the database blocks UPDATE and DELETE. "Duplicate & edit" saves a new row.

| Column | Type | Rules |
|--------|------|-------|
| id | BIGSERIAL | Primary key (internal) |
| quote_reference | VARCHAR(20) | Required, unique, shown to customers (e.g. `Q-7F3K9A`) |
| customer_id | BIGINT | Owner, FK → customer. Every read must filter by the session customer |
| request_key | UUID | Idempotency key from React. Unique per customer |
| quote_name | VARCHAR(100) | Required, not blank |
| rate_configuration_id | BIGINT | FK → rate_configuration |
| rate_version_code | VARCHAR(30) | Copy of the version code at save time |
| driver_age | INTEGER | 18–80 |
| vehicle_age | INTEGER | 0–20 |
| annual_mileage | INTEGER | 0–50,000 |
| prior_claims | INTEGER | 0–3 (fictional three-year lookback) |
| comparison_deductible | INTEGER | 500, 1000, or 2000. Applies to Standard/Enhanced only |
| selected_package | VARCHAR(10) | `BASIC`, `STANDARD`, or `ENHANCED` |
| basic_total / standard_total / enhanced_total | NUMERIC(12,2) | Annual USD estimates, ≥ 0 |
| snapshot | JSONB | Full pricing breakdowns (contract below) |
| duplicated_from_id | BIGINT | Optional FK → saved_quote, the quote it was duplicated from |
| created_at | TIMESTAMPTZ | Defaults to now |

**Save retry behavior:** if `(customer_id, request_key)` already exists, the insert fails with a
unique violation. T-06/T-09 should catch that and return the existing quote instead of an error.

## Snapshot contract (`saved_quote.snapshot`)

Proposed shape. **Confirm with T-05 and T-06 at kickoff**, then treat it as fixed.
Field names inside `factors` depend on T-08's rate configuration.

```json
{
  "schemaVersion": 1,
  "rateVersion": "TRAINING-1",
  "currency": "USD",
  "isTrainingEstimate": true,
  "inputs": {
    "driverAge": 30,
    "vehicleAge": 5,
    "annualMileage": 12000,
    "priorClaims": 0
  },
  "comparisonDeductible": 1000,
  "selectedPackage": "STANDARD",
  "packages": {
    "BASIC": {
      "coverages": ["LIABILITY"],
      "deductible": null,
      "basePremium": "600.00",
      "factors": [
        { "name": "driverAge",  "multiplier": "1.0000" },
        { "name": "vehicleAge", "multiplier": "1.0000" },
        { "name": "mileage",    "multiplier": "1.0000" },
        { "name": "claims",     "multiplier": "1.0000" }
      ],
      "adjustedLiabilityPremium": "600.00",
      "collisionPremium": null,
      "comprehensivePremium": null,
      "total": "600.00"
    },
    "STANDARD": {
      "coverages": ["LIABILITY", "COLLISION"],
      "deductible": 1000,
      "...": "same fields as BASIC, collisionPremium filled in"
    },
    "ENHANCED": {
      "coverages": ["LIABILITY", "COLLISION", "COMPREHENSIVE"],
      "deductible": 1000,
      "...": "same fields, collision and comprehensive filled in"
    }
  },
  "calculatedAt": "2026-10-22T14:05:00Z"
}
```

Rules:
- Money and multipliers are **strings** in JSON (`"600.00"`) so nothing between Java and React
  turns them into floating point. Java parses them with `new BigDecimal(...)`.
- `deductible` is `null` for Basic (React shows "Not applicable").
- The `*_total` columns must equal `packages.<PKG>.total`. Java writes both from the same calculation.
