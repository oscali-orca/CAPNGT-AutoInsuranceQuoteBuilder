# T-07 · Database setup (schema, migrations, seeded customers)

PostgreSQL 16 + Flyway. Migrations live in `src/main/resources/db/migration/`, the default
folder Spring Boot's Flyway integration scans, so they run automatically on app start.

| File | Owner | Purpose |
|------|-------|---------|
| `V1__create_core_tables.sql` | T-07 | `customer`, `rate_configuration`, `saved_quote` |
| `V2__seed_customers.sql` | T-07 | Two fictional customers |
| `V3__...` and up | T-08 | TRAINING-1 rate configuration |

**Never edit a migration after it has been pushed.** Add a new `V<n>__description.sql` instead,
or Flyway will refuse to start on everyone else's machine (checksum mismatch).

## Fresh setup

1. Start the database:
   ```bash
   docker compose up -d
   ```
2. Point Spring Boot at it (`application.properties`):
   ```properties
   spring.datasource.url=jdbc:postgresql://localhost:5432/quotes
   spring.datasource.username=quotes
   spring.datasource.password=quotes
   spring.flyway.enabled=true
   ```
   Also set `spring.jpa.hibernate.ddl-auto=validate` so Hibernate checks the tables instead of
   creating its own. Flyway dependencies (Spring Boot 4) are already in `pom.xml`:
   `spring-boot-starter-flyway` and `org.flywaydb:flyway-database-postgresql`.
3. Run the app. Flyway applies V1, V2, then T-08's rate migrations.

## Reset to a clean database

Option A, delete everything including the Docker volume (simplest):
```bash
docker compose down -v
docker compose up -d
```

Option B, keep the container and wipe the schema:
```bash
docker exec -i quote-training-db psql -U quotes -d quotes < scripts/reset-db.sql
```

Then restart the app so Flyway re-applies all migrations.

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
