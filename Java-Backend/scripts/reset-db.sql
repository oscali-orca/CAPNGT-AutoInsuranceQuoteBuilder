-- Wipes the local database back to empty. Run, then restart the app so Flyway
-- re-applies every migration (V1 schema, V2 customers, V3+ rates from T-08).
-- LOCAL USE ONLY.
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
