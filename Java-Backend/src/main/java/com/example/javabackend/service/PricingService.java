package com.example.javabackend.service;

import com.example.javabackend.dto.PricingRequest;
import com.example.javabackend.dto.PricingResponse;
import com.example.javabackend.dto.PricingResponse.PackageBreakdown;
import com.example.javabackend.dto.PricingResponse.RiskFactors;
import com.example.javabackend.enums.PackageType;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class PricingService {

    private static final BigDecimal BASE_PREMIUM =
            new BigDecimal("600.00");
    private static final BigDecimal ZERO_PREMIUM = new BigDecimal("0.00");

    public PricingResponse compare(PricingRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        validateRiskInputs(request.driverAge(), request.vehicleAge(),
                request.annualMileage(), request.priorClaims());
        if (request.comparisonDeductible() == null) {
            throw new IllegalArgumentException("comparison_deductible is required");
        }

        int deductible = request.comparisonDeductible();
        BigDecimal deductibleFactor = getDeductibleFactor(deductible);
        RiskFactors factors = calculateRiskFactors(request.driverAge(), request.vehicleAge(),
                request.annualMileage(), request.priorClaims());
        BigDecimal liability = calculateLiability(factors);
        BigDecimal collision = calculateCollision(deductible);
        BigDecimal comprehensive = calculateComprehensive(deductible);

        return new PricingResponse("TRAINING-1", "USD", "ANNUAL", factors,
                deductible, deductibleFactor,
                packageBreakdown(PackageType.BASIC, liability, ZERO_PREMIUM, ZERO_PREMIUM, null),
                packageBreakdown(PackageType.STANDARD, liability, collision, ZERO_PREMIUM, deductible),
                packageBreakdown(PackageType.ENHANCED, liability, collision, comprehensive, deductible));
    }

    private PackageBreakdown packageBreakdown(PackageType type, BigDecimal liability,
                                              BigDecimal collision, BigDecimal comprehensive,
                                              Integer deductible) {
        return new PackageBreakdown(type, liability, collision, comprehensive,
                deductible != null, deductible, liability.add(collision).add(comprehensive));
    }

    public BigDecimal calculateLiability(
            int driverAge,
            int vehicleAge,
            int annualMileage,
            int priorClaims) {

        validateRiskInputs(driverAge, vehicleAge, annualMileage, priorClaims);
        return calculateLiability(calculateRiskFactors(driverAge, vehicleAge, annualMileage, priorClaims));
    }

    private RiskFactors calculateRiskFactors(int driverAge, int vehicleAge,
                                             int annualMileage, int priorClaims) {
        // Driver age factor
        BigDecimal driverFactor;

        if (driverAge <= 24) {
            driverFactor = new BigDecimal("1.30");
        } else if (driverAge <= 64) {
            driverFactor = new BigDecimal("1.00");
        } else {
            driverFactor = new BigDecimal("1.15");
        }

        // Annual mileage factor
        BigDecimal mileageFactor;

        if (annualMileage <= 7500) {
            mileageFactor = new BigDecimal("0.90");
        } else if (annualMileage <= 15000) {
            mileageFactor = new BigDecimal("1.00");
        } else if (annualMileage <= 25000) {
            mileageFactor = new BigDecimal("1.10");
        } else {
            mileageFactor = new BigDecimal("1.20");
        }

        // Prior claims factor
        BigDecimal claimsFactor = BigDecimal.ONE.add(
                new BigDecimal("0.20")
                        .multiply(BigDecimal.valueOf(priorClaims))
        );

        // Vehicle age factor
        BigDecimal vehicleFactor = BigDecimal.ONE.add(
                new BigDecimal("0.01")
                        .multiply(BigDecimal.valueOf(20 - vehicleAge))
        );

        return new RiskFactors(driverFactor, mileageFactor, claimsFactor, vehicleFactor);
    }

    private BigDecimal calculateLiability(RiskFactors factors) {
        // Multiply exact factors, rounding only the completed liability component.
        return BASE_PREMIUM
                .multiply(factors.driverFactor())
                .multiply(factors.mileageFactor())
                .multiply(factors.claimsFactor())
                .multiply(factors.vehicleFactor())
                .setScale(2, RoundingMode.HALF_UP);
    }

    private void validateRiskInputs(Integer driverAge, Integer vehicleAge,
                                    Integer annualMileage, Integer priorClaims) {
        validateRange("driver_age", driverAge, 18, 80);
        validateRange("vehicle_age", vehicleAge, 0, 20);
        validateRange("annual_mileage", annualMileage, 0, 50000);
        validateRange("prior_claims", priorClaims, 0, 3);
    }

    private void validateRange(String field, Integer value, int minimum, int maximum) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(field + " must be between " + minimum + " and " + maximum);
        }
    }

    private BigDecimal getDeductibleFactor(int deductible) {

        return switch (deductible) {
            case 500 -> new BigDecimal("1.00");
            case 1000 -> new BigDecimal("0.85");
            case 2000 -> new BigDecimal("0.70");

            default -> throw new IllegalArgumentException(
                    "comparison_deductible must be 500, 1000, or 2000"
            );
        };
    }

    public BigDecimal calculateCollision(int deductible) {

        BigDecimal baseCollision = new BigDecimal("240.00");

        return baseCollision
                .multiply(getDeductibleFactor(deductible))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateComprehensive(int deductible) {

        BigDecimal baseComprehensive = new BigDecimal("120.00");

        return baseComprehensive
                .multiply(getDeductibleFactor(deductible))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
