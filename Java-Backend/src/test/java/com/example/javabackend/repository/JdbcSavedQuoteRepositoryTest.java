package com.example.javabackend.repository;

import com.example.javabackend.dto.PricingRequest;
import com.example.javabackend.dto.SaveQuoteRequest;
import com.example.javabackend.enums.PackageType;
import com.example.javabackend.service.PricingService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JdbcSavedQuoteRepositoryTest {
    private final RecordingJdbc jdbc = new RecordingJdbc();
    private final JdbcSavedQuoteRepository repository = new JdbcSavedQuoteRepository(jdbc);
    private static final UUID KEY = UUID.fromString("15c8b4ac-7b5a-49b5-b020-64bf35cab72e");
    private static final PricingRequest INPUTS = new PricingRequest(30, 3, 12000, 0, 1000);

    @Test
    void insertMatchesV1RequiredColumnsAndUsesBackendTotalsUuidKeyAndGeneratedId() throws Exception {
        var request = new SaveQuoteRequest("Commute", KEY, PackageType.STANDARD, INPUTS);
        var comparison = new PricingService().compare(INPUTS);
        repository.insert(31, request, 7, comparison, "{\"schema_version\":1}");
        var insert = Pattern.compile("INSERT INTO saved_quote\\s*\\(([^)]+)\\)").matcher(jdbc.sql);
        assertTrue(insert.find());
        List<String> columns = Arrays.stream(insert.group(1).split(",")).map(String::strip).toList();
        Path migration = Path.of("src/main/resources/db/migration/V1__create_core_tables.sql");
        if (!Files.exists(migration)) migration = Path.of("Java-Backend").resolve(migration);
        var table = Pattern.compile("(?s)CREATE TABLE saved_quote \\((.*?)\\n\\);")
                .matcher(Files.readString(migration));
        assertTrue(table.find());
        Set<String> schemaColumns = new HashSet<>();
        for (String line : table.group(1).split("\\R")) {
            var column = Pattern.compile("^\\s+(\\w+)\\s+(BIGSERIAL|BIGINT|UUID|VARCHAR|INTEGER|NUMERIC|JSONB|TIMESTAMPTZ)\\b")
                    .matcher(line);
            if (!column.find()) continue;
            schemaColumns.add(column.group(1));
            if (line.contains("NOT NULL") && !line.contains("DEFAULT")) {
                assertTrue(columns.contains(column.group(1)), "Missing required column: " + column.group(1));
            }
        }
        assertTrue(schemaColumns.containsAll(columns));
        assertFalse(columns.contains("id"));
        assertFalse(columns.contains("created_at"));
        assertEquals(columns.size(), jdbc.arguments.length);
        Map<String, Object> values = new HashMap<>();
        for (int index = 0; index < columns.size(); index++) values.put(columns.get(index), jdbc.arguments[index]);
        assertTrue(((String) values.get("quote_reference")).matches("Q-[0-9a-f]{18}"));
        assertEquals(31L, values.get("customer_id"));
        assertEquals(KEY, values.get("request_key"));
        assertEquals(7L, values.get("rate_configuration_id"));
        assertEquals(comparison.rateVersion(), values.get("rate_version_code"));
        assertEquals(30, values.get("driver_age"));
        assertEquals(3, values.get("vehicle_age"));
        assertEquals(12000, values.get("annual_mileage"));
        assertEquals(0, values.get("prior_claims"));
        assertEquals(1000, values.get("comparison_deductible"));
        assertEquals("STANDARD", values.get("selected_package"));
        assertEquals(comparison.basic().annualTotal(), values.get("basic_total"));
        assertEquals(comparison.standard().annualTotal(), values.get("standard_total"));
        assertEquals(comparison.enhanced().annualTotal(), values.get("enhanced_total"));
        assertEquals("{\"schema_version\":1}", values.get("snapshot"));
        assertTrue(jdbc.sql.contains("?::jsonb"));
        assertTrue(jdbc.sql.contains("ON CONFLICT ON CONSTRAINT saved_quote_customer_request_unique DO NOTHING"));
        assertTrue(jdbc.sql.contains("RETURNING"));
        assertFalse(jdbc.sql.contains("DO UPDATE"));
    }

    @Test
    void everyReadIsScopedToTheServerCustomerAndUsesActualIdentifiers() {
        repository.findByCustomerAndKey(31, KEY);
        assertTrue(jdbc.sql.contains("WHERE customer_id=? AND request_key=?"));
        assertArrayEquals(new Object[]{31L, KEY}, jdbc.arguments);
        repository.findOwnedById(31, 42);
        assertTrue(jdbc.sql.contains("WHERE customer_id=? AND id=?"));
        assertArrayEquals(new Object[]{31L, 42L}, jdbc.arguments);
        repository.findAllOwned(31);
        assertTrue(jdbc.sql.contains("WHERE customer_id=? ORDER BY created_at DESC, id DESC"));
        assertArrayEquals(new Object[]{31L}, jdbc.arguments);
    }

    @Test
    void mapsActualDatabaseColumnsAndNumericQuoteId() throws Exception {
        jdbc.result = mock(ResultSet.class);
        when(jdbc.result.getLong("id")).thenReturn(42L);
        when(jdbc.result.getLong("customer_id")).thenReturn(31L);
        when(jdbc.result.getString("quote_reference")).thenReturn("Q-42");
        when(jdbc.result.getObject("request_key", UUID.class)).thenReturn(KEY);
        when(jdbc.result.getString("quote_name")).thenReturn("Commute");
        when(jdbc.result.getString("selected_package")).thenReturn("STANDARD");
        when(jdbc.result.getInt("driver_age")).thenReturn(30);
        when(jdbc.result.getInt("vehicle_age")).thenReturn(3);
        when(jdbc.result.getInt("annual_mileage")).thenReturn(12000);
        when(jdbc.result.getInt("prior_claims")).thenReturn(0);
        when(jdbc.result.getInt("comparison_deductible")).thenReturn(1000);
        when(jdbc.result.getString("snapshot")).thenReturn("{}");
        when(jdbc.result.getTimestamp("created_at")).thenReturn(Timestamp.from(Instant.EPOCH));
        var row = repository.findOwnedById(31, 42).orElseThrow();
        assertEquals(42L, row.id());
        assertEquals("Q-42", row.quoteReference());
        assertEquals(31L, row.customerId());
        assertEquals(KEY, row.requestKey());
        assertEquals(INPUTS, row.pricingInputs());
        assertEquals(PackageType.STANDARD, row.selectedPackage());
        assertEquals("{}", row.snapshotJson());
        assertEquals(Instant.EPOCH, row.createdAt());
    }

    @Test
    void resolvesTheCalculatedVersionInsteadOfAnUnrelatedActiveVersion() throws Exception {
        jdbc.result = mock(ResultSet.class);
        when(jdbc.result.getLong("id")).thenReturn(7L);
        assertEquals(7L, repository.findRateConfigurationId("TRAINING-1").orElseThrow());
        assertEquals("SELECT id FROM rate_configuration WHERE version_code=?", jdbc.sql);
        assertArrayEquals(new Object[]{"TRAINING-1"}, jdbc.arguments);
    }

    @Test
    void emptyReturningResultMeansKeyConflictWithoutAnUpdate() {
        assertTrue(repository.insert(31, new SaveQuoteRequest("Name", KEY, PackageType.BASIC, INPUTS),
                7, new PricingService().compare(INPUTS), "{}").isEmpty());
        assertEquals(1, jdbc.calls);
    }

    @Test
    void unexpectedDatabaseFailuresPropagate() {
        jdbc.failure = new DataAccessResourceFailureException("Database unavailable");
        assertSame(jdbc.failure, assertThrows(DataAccessResourceFailureException.class,
                () -> repository.insert(31, new SaveQuoteRequest("Name", KEY, PackageType.BASIC, INPUTS),
                        7, new PricingService().compare(INPUTS), "{}")));
    }

    // Captures SQL and exercises the real row mapper; it does not execute SQL against PostgreSQL.
    private static final class RecordingJdbc extends JdbcTemplate {
        String sql;
        Object[] arguments;
        ResultSet result;
        RuntimeException failure;
        int calls;
        @Override public <T> List<T> query(String sql, RowMapper<T> mapper, Object... arguments) {
            this.sql = sql;
            this.arguments = arguments;
            calls++;
            if (failure != null) throw failure;
            if (result == null) return List.of();
            try { return List.of(mapper.mapRow(result, 0)); }
            catch (SQLException exception) { throw new IllegalStateException(exception); }
        }
    }
}
