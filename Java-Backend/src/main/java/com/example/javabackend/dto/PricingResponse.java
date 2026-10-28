package com.example.javabackend.dto;

import com.example.javabackend.enums.PackageType;
import java.math.BigDecimal;

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
    public record RiskFactors(
            BigDecimal driverFactor,
            BigDecimal mileageFactor,
            BigDecimal claimsFactor,
            BigDecimal vehicleFactor
    ) {}

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
