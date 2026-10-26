INSERT INTO rate_configuration (version_code,
                                description,
                                currency,
                                is_active,
                                config);
VALUES ('TRAINING-1',
        'Fictional pricing rules for training exercise',
        'USD',
        TRUE,
        '{
          "base_premium": 600.00,
          "currency": "USD",
          "period": "annual",
          "rounding": {
            "liability_premium": "HALF_UP_2_DECIMALS",
            "addon": "HALF_UP_2_DECIMALS",
            "package_total": "HALF_UP_2_DECIMALS"
          },
          "driver_factors": [
            {"age_min": 18, "age_max": 24, "factor": 1.30},
            {"age_min": 25, "age_max": 64, "factor": 1.00},
            {"age_min": 65, "age_max": 80, "factor": 1.15}
          ],
          "mileage_factors": [
            {"mileage_min": 0, "mileage_max": 7500, "factor": 0.90},
            {"mileage_min": 7501, "mileage_max": 15000, "factor": 1.00},
            {"mileage_min": 15001, "mileage_max": 25000, "factor": 1.10},
            {"mileage_min": 25001, "mileage_max": 50000, "factor": 1.20}
          ],
          "claims_factor_formula": "1 + (0.20 * claims_count)",
          "vehicle_age_formula": "1 + ((20 - vehicle_age_years) * 0.01)",
          "packages": {
            "BASIC": {
              "description": "Adjusted liability premium only",
              "deductible_applies": false
            },
            "STANDARD": {
              "description": "Adjusted liability premium + collision add-on",
              "deductible_applies": true,
              "collision_cost": 240.00
            },
            "ENHANCED": {
              "description": "Adjusted liability premium + collision + comprehensive add-ons",
              "deductible_applies": true,
              "collision_cost": 240.00,
              "comprehensive_cost": 120.00
            }
          },
          "deductible_factors": {
            "500": 1.00,
            "1000": 0.85,
            "2000": 0.70
          }
        }'::jsonb);

