# React Front End

The frontend for the CAPNGT Auto Insurance Quote Builder uses React, TypeScript, and Vite.

## Getting started

Install dependencies and start the development server:

```sh
npm install
npm run dev
```

Create a production build with `npm run build`.

## Coverage builder

The `/coverage` route is Step 2: it reads validated risk details from
`QuoteDraftContext`, lets the user select one package and deductible, and shows
that package's price details. The `/comparison` route is Step 3 and compares all
three packages. The selected package and deductible carry across both routes.

Pricing currently uses a clearly labeled fictional `TRAINING-1` sample response
in `src/services/pricing.ts`; it is not connected to the Java pricing API. With
the worked-example inputs (driver age 30, vehicle age 3, 12,000 annual miles,
and 0 prior claims), the $1,000 deductible produces annual estimates of $702
(Basic), $906 (Standard), and $1,008 (Enhanced). Replace the sample calculation
with the Java API adapter once its endpoint and response contract are ready.

The comparison page can save a named quote snapshot in the current browser's
local storage. It stores the selected package, risk inputs, all package prices,
deductible, and rate version; it does not submit quotes to a server.
