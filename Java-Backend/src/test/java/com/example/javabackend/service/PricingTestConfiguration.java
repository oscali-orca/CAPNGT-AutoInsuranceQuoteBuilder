package com.example.javabackend.service;

import com.example.javabackend.dto.QuoteConfigResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class PricingTestConfiguration {
    private PricingTestConfiguration() {}

    public static PricingService pricingService() {
        QuoteConfigService configService = mock(QuoteConfigService.class);
        when(configService.findActiveConfiguration()).thenReturn(Optional.of(new QuoteConfigResponse(
                "TRAINING-1", "Test pricing rules", "USD", Map.of(
                        "base_premium", new BigDecimal("600.00"),
                        "period", "annual",
                        "rounding", Map.of(
                                "liability_premium", "HALF_UP_2_DECIMALS",
                                "addon", "HALF_UP_2_DECIMALS",
                                "package_total", "HALF_UP_2_DECIMALS"),
                        "driver_factors", List.of(
                                Map.of("age_min", 18, "age_max", 24, "factor", new BigDecimal("1.30")),
                                Map.of("age_min", 25, "age_max", 64, "factor", new BigDecimal("1.00")),
                                Map.of("age_min", 65, "age_max", 80, "factor", new BigDecimal("1.15"))),
                        "mileage_factors", List.of(
                                Map.of("mileage_min", 0, "mileage_max", 7500, "factor", new BigDecimal("0.90")),
                                Map.of("mileage_min", 7501, "mileage_max", 15000, "factor", new BigDecimal("1.00")),
                                Map.of("mileage_min", 15001, "mileage_max", 25000, "factor", new BigDecimal("1.10")),
                                Map.of("mileage_min", 25001, "mileage_max", 50000, "factor", new BigDecimal("1.20"))),
                        "claims_factor_formula", "1 + (0.20 * claims_count)",
                        "vehicle_age_formula", "1 + ((20 - vehicle_age_years) * 0.01)",
                        "packages", Map.of(
                                "BASIC", Map.of("deductible_applies", false),
                                "STANDARD", Map.of("deductible_applies", true,
                                        "collision_cost", new BigDecimal("240.00")),
                                "ENHANCED", Map.of("deductible_applies", true,
                                        "collision_cost", new BigDecimal("240.00"),
                                        "comprehensive_cost", new BigDecimal("120.00"))),
                        "deductible_factors", Map.of(
                                "500", new BigDecimal("1.00"), "1000", new BigDecimal("0.85"),
                                "2000", new BigDecimal("0.70"))))));
        return new PricingService(configService);
    }
}
