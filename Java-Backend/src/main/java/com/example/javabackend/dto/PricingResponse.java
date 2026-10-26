package com.example.javabackend.dto;

import com.example.javabackend.enums.PackageType;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PricingResponse(
        String rateVersion,
        String currency,
        String period,
        RiskFactors riskFactors,
        int comparisonDeductible,
        BigDecimal deductibleFactor,
        PackageBreakdown basic,
        PackageBreakdown standard,
        PackageBreakdown enhanced
) {
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record RiskFactors(
            BigDecimal driverFactor,
            BigDecimal mileageFactor,
            BigDecimal claimsFactor,
            BigDecimal vehicleFactor
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record PackageBreakdown(
            PackageType packageType,
            BigDecimal liability,
            BigDecimal collision,
            BigDecimal comprehensive,
            boolean deductibleApplicable,
            Integer deductible,
            BigDecimal annualTotal
    ) {}
}
