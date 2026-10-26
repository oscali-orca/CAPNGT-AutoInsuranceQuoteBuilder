package com.example.javabackend.repository;

import com.example.javabackend.dto.*;
import com.example.javabackend.enums.PackageType;
import com.example.javabackend.service.PricingService;
import com.example.javabackend.service.SavedQuoteService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UNVERIFIED without a dedicated, disposable local PostgreSQL database named task6_test.
 * Opt in with TASK6_TEST_DB_URL, TASK6_TEST_DB_USER and TASK6_TEST_DB_PASSWORD.
 * Never reads application.properties or the team's DB_URL. Each test creates and
 * drops only its own random schema, applying existing V1/V2 SQL there unchanged.
 * This verifies JDBC/PostgreSQL behavior, not Flyway migration discovery.
 */
@Tag("postgresql")
@EnabledIfEnvironmentVariable(named = "TASK6_TEST_DB_URL",
        matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1)(:\\d+)?/task6_test(\\?.*)?")
class JdbcSavedQuoteRepositoryPostgresTest {
    private static final PricingRequest INPUTS = new PricingRequest(30, 3, 12000, 0, 1000);
    private DriverManagerDataSource admin;
    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private JdbcSavedQuoteRepository repository;
    private String schema;
    private long customerId;

    @BeforeEach
    void createIsolatedSchema() throws Exception {
        String url = Objects.requireNonNull(System.getenv("TASK6_TEST_DB_URL"));
        String user = Objects.requireNonNull(System.getenv("TASK6_TEST_DB_USER"), "TASK6_TEST_DB_USER is required");
        String password = System.getenv("TASK6_TEST_DB_PASSWORD");
        admin = new DriverManagerDataSource(url, user, password);
        schema = "task6_it_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(admin).execute("CREATE SCHEMA " + schema);
        dataSource = new DriverManagerDataSource(url, user, password) {
            @Override public Connection getConnection() throws SQLException {
                Connection connection = super.getConnection();
                try {
                    connection.setSchema(schema);
                    return connection;
                } catch (SQLException failure) {
                    connection.close();
                    throw failure;
                }
            }
        };
        try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute(migration("V1__create_core_tables.sql"));
            statement.execute(migration("V2__seed_customers.sql"));
        }
        jdbc = new JdbcTemplate(dataSource);
        // Version/FK fixture only; premium formulas always come from the unchanged PricingService.
        jdbc.update("INSERT INTO rate_configuration (version_code, is_active, config) VALUES (?, TRUE, '{}'::jsonb)",
                "TRAINING-1");
        customerId = jdbc.queryForObject("SELECT id FROM customer WHERE username=?", Long.class, "alice.training");
        repository = new JdbcSavedQuoteRepository(jdbc);
    }

    @AfterEach
    void removeOnlyThisTestSchema() {
        if (admin != null && schema != null) {
            assertTrue(schema.matches("task6_it_[0-9a-f]{32}"));
            new JdbcTemplate(admin).execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void persistsActualSchemaTotalsRateForeignKeySnapshotAndCustomerOwnership() {
        SavedQuoteService service = service(new PricingService(), customerId);
        SaveQuoteRequest request = request();
        SavedQuoteResponse saved = transaction().execute(status -> service.save(request));
        assertNotNull(saved);
        assertEquals(saved, service.get(saved.id()));
        assertEquals(saved, transaction().execute(status -> service.save(request)));
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM saved_quote", Long.class));
        assertEquals(saved.snapshot().comparison().basic().annualTotal(),
                jdbc.queryForObject("SELECT basic_total FROM saved_quote WHERE id=?", java.math.BigDecimal.class, saved.id()));
        assertEquals(saved.snapshot().comparison().standard().annualTotal(),
                jdbc.queryForObject("SELECT standard_total FROM saved_quote WHERE id=?", java.math.BigDecimal.class, saved.id()));
        assertEquals(saved.snapshot().comparison().enhanced().annualTotal(),
                jdbc.queryForObject("SELECT enhanced_total FROM saved_quote WHERE id=?", java.math.BigDecimal.class, saved.id()));
        assertEquals("TRAINING-1", jdbc.queryForObject("""
                SELECT rc.version_code FROM saved_quote sq
                JOIN rate_configuration rc ON rc.id = sq.rate_configuration_id WHERE sq.id=?
                """, String.class, saved.id()));
        long otherId = jdbc.queryForObject("SELECT id FROM customer WHERE username=?", Long.class, "bob.training");
        SavedQuoteService other = service(new PricingService(), otherId);
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> other.get(saved.id())).getStatusCode().value());
        assertTrue(other.list().isEmpty());
    }

    @Test
    void realReadCommittedInsertRaceReturnsOneQuoteToBothCallers() throws Exception {
        CyclicBarrier bothMissedInitialLookup = new CyclicBarrier(2);
        PricingService concurrentPricing = new PricingService() {
            @Override public PricingResponse compare(PricingRequest inputs) {
                try { bothMissedInitialLookup.await(10, TimeUnit.SECONDS); }
                catch (Exception failure) { throw new IllegalStateException(failure); }
                return super.compare(inputs);
            }
        };
        SavedQuoteService service = service(concurrentPricing, customerId);
        SaveQuoteRequest request = request();
        try (ExecutorService threads = Executors.newFixedThreadPool(2)) {
            Future<SavedQuoteResponse> first = threads.submit(() -> transaction().execute(status -> service.save(request)));
            Future<SavedQuoteResponse> second = threads.submit(() -> transaction().execute(status -> service.save(request)));
            assertEquals(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        }
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM saved_quote", Long.class));
    }

    @Test
    void databaseTriggerRejectsUpdatesAndDeletesAndKeepsOriginalSnapshot() {
        SavedQuoteService service = service(new PricingService(), customerId);
        SavedQuoteResponse saved = transaction().execute(status -> service.save(request()));
        assertNotNull(saved);
        assertThrows(DataAccessException.class,
                () -> jdbc.update("UPDATE saved_quote SET quote_name=? WHERE id=?", "changed", saved.id()));
        assertThrows(DataAccessException.class, () -> jdbc.update("DELETE FROM saved_quote WHERE id=?", saved.id()));
        assertEquals(saved, service.get(saved.id()));
    }

    private SavedQuoteService service(PricingService pricing, long owner) {
        return new SavedQuoteService(pricing, repository, () -> owner, JsonMapper.builder().build());
    }

    private TransactionTemplate transaction() {
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(15);
        return transaction;
    }

    private static SaveQuoteRequest request() {
        return new SaveQuoteRequest("Commute", UUID.randomUUID(), PackageType.STANDARD, INPUTS);
    }

    private static String migration(String name) throws Exception {
        Path file = Path.of("src/main/resources/db/migration", name);
        if (!Files.exists(file)) file = Path.of("Java-Backend").resolve(file);
        return Files.readString(file);
    }
}
