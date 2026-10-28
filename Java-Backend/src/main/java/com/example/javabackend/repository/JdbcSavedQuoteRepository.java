package com.example.javabackend.repository;

import com.example.javabackend.dto.PricingRequest;
import com.example.javabackend.dto.PricingResponse;
import com.example.javabackend.dto.SaveQuoteRequest;
import com.example.javabackend.enums.PackageType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** JDBC mapping for the existing V1 saved_quote schema; inserts only. */
@Repository
public class JdbcSavedQuoteRepository {
    public record Row(long id, String quoteReference, long customerId, UUID requestKey,
                      String quoteName, PackageType selectedPackage, PricingRequest pricingInputs,
                      String snapshotJson, Instant createdAt) {}

    private static final String COLUMNS = """
            id, quote_reference, customer_id, request_key, quote_name, selected_package,
            driver_age, vehicle_age, annual_mileage, prior_claims, comparison_deductible,
            snapshot, created_at
            """;
    private final JdbcTemplate jdbc;
    public JdbcSavedQuoteRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<Long> findCustomerIdByUsername(String username) {
        return jdbc.query("SELECT id FROM customer WHERE username=?",
                (rs, rowNum) -> rs.getLong("id"), username).stream().findFirst();
    }

    private Row map(ResultSet rs, int unused) throws SQLException {
        return new Row(rs.getLong("id"), rs.getString("quote_reference"), rs.getLong("customer_id"),
                rs.getObject("request_key", UUID.class),
                rs.getString("quote_name"), PackageType.valueOf(rs.getString("selected_package")),
                new PricingRequest(rs.getInt("driver_age"), rs.getInt("vehicle_age"),
                        rs.getInt("annual_mileage"), rs.getInt("prior_claims"),
                        rs.getInt("comparison_deductible")), rs.getString("snapshot"),
                rs.getTimestamp("created_at").toInstant());
    }

    public Optional<Row> findByCustomerAndKey(long customerId, UUID key) {
        return jdbc.query("SELECT " + COLUMNS + " FROM saved_quote WHERE customer_id=? AND request_key=?",
                this::map, customerId, key).stream().findFirst();
    }

    public Optional<Row> findOwnedById(long customerId, long quoteId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM saved_quote WHERE customer_id=? AND id=?",
                this::map, customerId, quoteId).stream().findFirst();
    }

    public List<Row> findAllOwned(long customerId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM saved_quote WHERE customer_id=? ORDER BY created_at DESC, id DESC",
                this::map, customerId);
    }

    public Optional<Long> findRateConfigurationId(String versionCode) {
        return jdbc.query("SELECT id FROM rate_configuration WHERE version_code=?",
                (rs, rowNum) -> rs.getLong("id"), versionCode).stream().findFirst();
    }

    public Optional<Row> insert(long customerId, SaveQuoteRequest request, long rateConfigurationId,
                                          PricingResponse comparison, String snapshotJson) {
        PricingRequest inputs = request.pricingInputs();
        String reference = "Q-" + UUID.randomUUID().toString().replace("-", "").substring(0, 18);
        return jdbc.query("""
                INSERT INTO saved_quote (
                    quote_reference, customer_id, request_key, quote_name,
                    rate_configuration_id, rate_version_code, driver_age, vehicle_age,
                    annual_mileage, prior_claims, comparison_deductible, selected_package,
                    basic_total, standard_total, enhanced_total, snapshot
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                ON CONFLICT ON CONSTRAINT saved_quote_customer_request_unique DO NOTHING
                RETURNING
                """ + COLUMNS, this::map,
                reference, customerId, request.saveRequestKey(), request.quoteName(),
                rateConfigurationId, comparison.rateVersion(), inputs.driverAge(), inputs.vehicleAge(),
                inputs.annualMileage(), inputs.priorClaims(), inputs.comparisonDeductible(),
                request.selectedPackage().name(), comparison.basic().annualTotal(),
                comparison.standard().annualTotal(), comparison.enhanced().annualTotal(), snapshotJson)
                .stream().findFirst();
    }
}
