package com.example.javabackend.service;

import com.example.javabackend.dto.QuoteConfigResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class QuoteConfigService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public QuoteConfigService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<QuoteConfigResponse> findActiveConfiguration() {
        List<RateConfigRow> rows = jdbcTemplate.query(
                "SELECT version_code, description, currency, config::text AS config_json " +
                        "FROM rate_configuration WHERE is_active = TRUE",
                (rs, rowNum) -> new RateConfigRow(
                        rs.getString("version_code"),
                        rs.getString("description"),
                        rs.getString("currency"),
                        rs.getString("config_json")));

        if (rows.isEmpty()) {
            return Optional.empty();
        }

        RateConfigRow row = rows.getFirst();
        try {
            Map<String, Object> config = objectMapper.readerFor(new TypeReference<Map<String, Object>>() {})
                    .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .readValue(row.configJson());
            return Optional.of(new QuoteConfigResponse(
                    row.versionCode(), row.description(), row.currency(), config));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Active rate configuration contains invalid JSON", ex);
        }
    }

    private record RateConfigRow(String versionCode, String description, String currency, String configJson) {
    }
}
