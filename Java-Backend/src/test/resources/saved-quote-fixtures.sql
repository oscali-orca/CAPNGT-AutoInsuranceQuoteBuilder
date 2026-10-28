-- T-09 test data: two test-only customers, 3 quotes for the first and 1 for the second.
-- Runs inside each test's transaction and is rolled back afterwards, so it is never kept.

INSERT INTO customer (username, display_name, password_hash)
VALUES ('t09.alice.test', 'T-09 Test Alice', 'not-a-real-login'),
       ('t09.bob.test', 'T-09 Test Bob', 'not-a-real-login');

INSERT INTO saved_quote (quote_reference, customer_id, request_key, quote_name,
                         rate_configuration_id, rate_version_code,
                         driver_age, vehicle_age, annual_mileage, prior_claims,
                         comparison_deductible, selected_package,
                         basic_total, standard_total, enhanced_total, snapshot, created_at)
VALUES
    ('Q-T09A01', (SELECT id FROM customer WHERE username = 't09.alice.test'), '00000000-0000-0000-0000-00000000a001', 'Alice first quote',
     (SELECT id FROM rate_configuration WHERE version_code = 'TRAINING-1'), 'TRAINING-1',
     30, 3, 12000, 0, 1000, 'STANDARD', 702.00, 906.00, 1008.00, '{}', '2026-10-19 09:00:00+00'),

    ('Q-T09A02', (SELECT id FROM customer WHERE username = 't09.alice.test'), '00000000-0000-0000-0000-00000000a002', 'Alice second quote',
     (SELECT id FROM rate_configuration WHERE version_code = 'TRAINING-1'), 'TRAINING-1',
     30, 3, 12000, 0, 2000, 'ENHANCED', 702.00, 870.00, 954.00, '{}', '2026-10-20 10:00:00+00'),

    ('Q-T09A03', (SELECT id FROM customer WHERE username = 't09.alice.test'), '00000000-0000-0000-0000-00000000a003', 'Alice third quote',
     (SELECT id FROM rate_configuration WHERE version_code = 'TRAINING-1'), 'TRAINING-1',
     30, 3, 12000, 0, 500, 'BASIC', 702.00, 942.00, 1062.00, '{}', '2026-10-20 10:00:00+00'),

    ('Q-T09B01', (SELECT id FROM customer WHERE username = 't09.bob.test'), '00000000-0000-0000-0000-00000000b001', 'Bob quote',
     (SELECT id FROM rate_configuration WHERE version_code = 'TRAINING-1'), 'TRAINING-1',
     70, 0, 5000, 2, 1000, 'STANDARD', 1043.28, 1247.28, 1349.28, '{}', '2026-10-21 15:30:00+00');
