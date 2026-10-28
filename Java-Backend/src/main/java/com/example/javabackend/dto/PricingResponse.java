package com.example.javabackend.dto;

import com.example.javabackend.enums.PackageType;
import com.fasterxml.jackson.annotation.JsonAlias;
import java.math.BigDecimal;

public record PricingResponse(
        @JsonAlias("rate_version") String rateVersion,
        String currency,
        String period,
        @JsonAlias("risk_factors") RiskFactors riskFactors,
        @JsonAlias("comparison_deductible") int comparisonDeductible,
        @JsonAlias("deductible_factor") BigDecimal deductibleFactor,
        PackageBreakdown basic,
        PackageBreakdown standard,
        PackageBreakdown enhanced
) {
    public record RiskFactors(
            @JsonAlias("driver_factor") BigDecimal driverFactor,
            @JsonAlias("mileage_factor") BigDecimal mileageFactor,
            @JsonAlias("claims_factor") BigDecimal claimsFactor,
            @JsonAlias("vehicle_factor") BigDecimal vehicleFactor
    ) {}

    public record PackageBreakdown(
            @JsonAlias("package_type") PackageType packageType,
            BigDecimal liability,
            BigDecimal collision,
            BigDecimal comprehensive,
            @JsonAlias("deductible_applicable") boolean deductibleApplicable,
            Integer deductible,
            @JsonAlias("annual_total") BigDecimal annualTotal
    ) {}
}
