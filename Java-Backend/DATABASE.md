# T-07 · Database setup (schema, migrations, seeded customers)

PostgreSQL 16 + Flyway. Migrations live in `src/main/resources/db/migration/`, the default
folder Spring Boot's Flyway integration scans, so they run automatically on app start.

| File | Owner | Purpose |
|------|-------|---------|
| `20261027182126_new-migration.sql` | T-07 | `customer`, `rate_configuration`, `saved_quote` |
| `20261027182151_new-migration.sql` | T-07 | Two fictional customers |
| `20261027182204_new-migration.sql` | T-08 | TRAINING-1 rate configuration |

**Never edit a migration after it has been pushed.** Add a new `V<n>__description.sql` instead,
or Flyway will refuse to start on everyone else's machine (checksum mismatch).

##  setup

1. Login to the database:
   ```bash
   supabase login
   ```
2. Then link correct supabase db project to this project
   ```supabase link --project-ref <project-ref-id>```

## Seeded customers (fictional, local testing only)

| Username | Password | Display name |
|----------|----------|--------------|
| `alice.training` | `Training-Alice-1` | Alice Example |
| `bob.training` | `Training-Bob-1` | Bob Sample |

Passwords are stored as BCrypt hashes. Verify with Spring Security's `BCryptPasswordEncoder`.
Use both accounts to test that one customer can't see the other's quotes (US-04).

## Handoffs

- **T-08:** insert the TRAINING-1 row into `rate_configuration` with `is_active = true` in a
  `V3__` migration. Define the `config` JSON shape and add it to `DATA_DICTIONARY.md`.
- **T-04:** authenticate against `customer.username` / `password_hash`; read the active rate
  version with `WHERE is_active`.
- **T-06 / T-09:** follow the snapshot contract in `DATA_DICTIONARY.md`. Saved quotes are
  insert-only; the database rejects UPDATE and DELETE.
