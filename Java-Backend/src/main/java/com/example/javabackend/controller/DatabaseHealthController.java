package com.example.javabackend.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class DatabaseHealthController {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseHealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Confirms which database the app reached and that Flyway's migrations and seed data are there
    @GetMapping("/db")
    public Map<String, Object> database() {
        return jdbcTemplate.queryForMap("""
                SELECT current_database()                AS database,
                       current_user                      AS db_user,
                       current_setting('server_version') AS server_version,
                       (SELECT version FROM flyway_schema_history
                         WHERE success AND version IS NOT NULL
                         ORDER BY installed_rank DESC
                         LIMIT 1)                        AS schema_version,
                       (SELECT count(*) FROM customer)   AS seeded_customers
                """);
    }
}
