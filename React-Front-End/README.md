# React Front End

The frontend for the CAPNGT Auto Insurance Quote Builder uses React, TypeScript, and Vite.

## Getting started

With dependencies installed, start the development server from this directory:

```sh
npm run dev
```

Create a production build with `npm run build`.

Vite uses `http://localhost:5173` and fails if that port is occupied, matching
the backend CORS origin. API requests go directly to `http://localhost:8080`
with session cookies; no Vite proxy is needed. A fresh checkout needs `npm ci`.

## Local backend and database

Open `Java-Backend` as a Maven project in IntelliJ and run
`com.example.javabackend.JavaBackendApplication` on port 8080, with
`Java-Backend` as the working directory and a JDK supporting the Java 21 target.
Keep the team's approved `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` in the local
Run Configuration. Do not commit their values.

For this local integration demo against the existing shared Supabase database,
set `SPRING_FLYWAY_ENABLED=false` in that IntelliJ Run Configuration only.
Migrations now live in `Java-Backend/supabase/migrations`, outside Flyway's
default classpath location. Do not run or rename them to resolve the warning.
Keep `spring.jpa.hibernate.ddl-auto=validate`; do not disable Flyway globally.

The database owner must confirm the existing `customer`, `rate_configuration`,
and `saved_quote` tables, the application role's permissions, the assigned
seeded training accounts, and an active `TRAINING-1` rate row. New quote saves
require a row matching the calculated rate version. No data is seeded by React.

## Authentication and API behavior

Sign in with an assigned username (for example, `alice.training`) and its
password. The frontend fetches `/api/csrf`, then posts to `/api/session`.
The existing HTTP helper sends cookies on every request and fetches a CSRF
token for POST/DELETE requests. Credentials are not stored in browser storage.
Use the exported `signOut()` helper for the existing DELETE session endpoint.

## Coverage builder

The `/coverage` route is Step 2: it reads validated risk details from
`QuoteDraftContext`, lets the user select one package and deductible, and shows
that package's price details. The `/comparison` route is Step 3 and compares all
three packages. The selected package and deductible carry across both routes.

Pricing uses `POST /api/pricing/compare` in the Java backend. With
the worked-example inputs (driver age 30, vehicle age 3, 12,000 annual miles,
and 0 prior claims), the $1,000 deductible produces annual estimates of $702
(Basic), $906 (Standard), and $1,008 (Enhanced). The frontend formats the returned
amounts; it does not implement premium formulas.

The comparison page saves through `POST /api/quotes`. It sends the name,
selected package, risk inputs, deductible, and UUID `saveRequestKey` in JSON;
the backend calculates and stores the prices. Retrying an unchanged save reuses
the key. A changed payload or a newly opened quote form gets a new key.

The saved-quotes page reads stored snapshots without recalculating historical
prices. Duplicate & edit requests an unsaved draft with current prices; only
the subsequent save inserts a new record. The original remains unchanged.

Leave `VITE_USE_MOCK` unset or false for the Supabase integration demo. Setting
it to true uses separate browser-only quote storage for UI work, while login
and pricing still use Java. Mock mode is not a database or ownership demo.

## Manual demo checks

1. Start the backend and Vite separately; open `http://localhost:5173`.
2. Sign in, enter the worked-example inputs, and compare the three totals above.
3. Save a named quote, then open Saved quotes and view its stored breakdown.
4. Retry the unchanged save and confirm it returns the same quote reference.
5. Duplicate a saved quote, edit the draft, compare again, and save it with a new reference.
6. Verify the original snapshot is unchanged and another account cannot retrieve it.

These steps write quote records and are for the team's approved manual demo.
Build success alone does not verify cookies, live schema/seed data, or persistence.
If older saved snapshots contain snake_case pricing response fields, their
compatibility with the current camelCase Java pricing DTO still needs checking
with the backend/database owners; do not rewrite historical prices.
