-- T-07 · Fictional seeded customers (training data only)
-- Plain-text passwords for local testing are listed in README.md.
-- Hashes are BCrypt ($2a$, cost 10), compatible with Spring Security's BCryptPasswordEncoder.

INSERT INTO customer (username, display_name, password_hash) VALUES
    ('alice.training', 'Alice Example', '$2a$10$O6tzWWMzfhiOAlARSlVB/OQFSoy3mRWYyz59DiQFVwXwaor2V/n5.'),
    ('bob.training',   'Bob Sample',    '$2a$10$X7MKPsbzgURJHBEhQd/nrehRWSO2HDACaITbRSmo1oHoZ2uHczZGe');

-- Rate configuration rows are NOT inserted here. T-08 adds them in V3+.
