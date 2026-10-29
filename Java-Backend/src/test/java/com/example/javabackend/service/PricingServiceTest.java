package com.example.javabackend.service;

import com.example.javabackend.dto.PricingRequest;
import com.example.javabackend.dto.PricingResponse;
import com.example.javabackend.dto.PricingResponse.PackageBreakdown;
import com.example.javabackend.enums.PackageType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PricingServiceTest {
    private final PricingService service = PricingTestConfiguration.pricingService();

    @Test
    void benchmarkReturnsAllThreeAnnualUsdPackages() {
        PricingResponse result = service.compare(new PricingRequest(30, 3, 12000, 0, 1000));

        assertEquals("TRAINING-1", result.rateVersion());
        assertEquals("USD", result.currency());
        assertEquals("ANNUAL", result.period());
        assertEquals(1000, result.comparisonDeductible());
        assertEquals(new BigDecimal("0.85"), result.deductibleFactor());
        assertEquals(new PricingResponse.RiskFactors(new BigDecimal("1.00"), new BigDecimal("1.00"),
                new BigDecimal("1.00"), new BigDecimal("1.17")), result.riskFactors());
        assertEquals(PackageType.BASIC, result.basic().packageType());
        assertEquals(PackageType.STANDARD, result.standard().packageType());
        assertEquals(PackageType.ENHANCED, result.enhanced().packageType());
        assertMoney("702.00", result.basic().annualTotal());
        assertMoney("906.00", result.standard().annualTotal());
        assertMoney("1008.00", result.enhanced().annualTotal());
    }

    @ParameterizedTest
    @CsvSource({"18,1.30", "24,1.30", "25,1.00", "64,1.00", "65,1.15", "80,1.15"})
    void driverFactorBoundaries(int driverAge, String expectedFactor) {
        assertEquals(new BigDecimal(expectedFactor), service.compare(
                new PricingRequest(driverAge, 3, 12000, 0, 1000)).riskFactors().driverFactor());
    }

    @ParameterizedTest
    @CsvSource({"0,0.90", "7500,0.90", "7501,1.00", "15000,1.00", "15001,1.10",
            "25000,1.10", "25001,1.20", "50000,1.20"})
    void mileageFactorBoundaries(int mileage, String expectedFactor) {
        assertEquals(new BigDecimal(expectedFactor), service.compare(
                new PricingRequest(30, 3, mileage, 0, 1000)).riskFactors().mileageFactor());
    }

    @ParameterizedTest
    @CsvSource({"0,0,1.20,1.00", "20,3,1.00,1.60", "3,1,1.17,1.20", "3,2,1.17,1.40"})
    void vehicleAndClaimsFactors(int vehicleAge, int claims, String vehicleFactor, String claimsFactor) {
        PricingResponse result = service.compare(new PricingRequest(30, vehicleAge, 12000, claims, 1000));
        assertEquals(new BigDecimal(vehicleFactor), result.riskFactors().vehicleFactor());
        assertEquals(new BigDecimal(claimsFactor), result.riskFactors().claimsFactor());
    }

    @ParameterizedTest
    @CsvSource({"500,1.00,240.00,120.00,942.00,1062.00",
            "1000,0.85,204.00,102.00,906.00,1008.00",
            "2000,0.70,168.00,84.00,870.00,954.00"})
    void deductibleChangesOnlyCoveredComponents(int deductible, String factor, String collision,
                                               String comprehensive, String standard, String enhanced) {
        PricingResponse result = service.compare(new PricingRequest(30, 3, 12000, 0, deductible));
        assertEquals(new BigDecimal(factor), result.deductibleFactor());
        assertMoney("702.00", result.basic().annualTotal());
        assertFalse(result.basic().deductibleApplicable());
        assertNull(result.basic().deductible());
        assertMoney("0.00", result.basic().collision());
        assertMoney("0.00", result.basic().comprehensive());
        assertTrue(result.standard().deductibleApplicable());
        assertTrue(result.enhanced().deductibleApplicable());
        assertEquals(deductible, result.standard().deductible());
        assertEquals(deductible, result.enhanced().deductible());
        assertMoney(collision, result.standard().collision());
        assertMoney(collision, result.enhanced().collision());
        assertMoney("0.00", result.standard().comprehensive());
        assertMoney(comprehensive, result.enhanced().comprehensive());
        assertMoney(standard, result.standard().annualTotal());
        assertMoney(enhanced, result.enhanced().annualTotal());
        assertComponentTotals(result);
    }

    @ParameterizedTest
    @CsvSource({"1,886.79,1.19", "3,871.88,1.17"})
    void roundsCompletedLiabilityBeforeAddingComponents(int vehicleAge, String expected, String vehicleFactor) {
        PricingResponse result = service.compare(new PricingRequest(65, vehicleAge, 7500, 1, 1000));
        assertMoney(expected, result.basic().liability());
        assertMoney(expected, service.calculateLiability(65, vehicleAge, 7500, 1));
        assertEquals(new BigDecimal(vehicleFactor), result.riskFactors().vehicleFactor());
        assertComponentTotals(result);
    }

    @ParameterizedTest
    @CsvSource({"18,0,0,0", "80,20,50000,3"})
    void acceptsInclusiveRiskLimits(int driverAge, int vehicleAge, int mileage, int claims) {
        assertComponentTotals(service.compare(new PricingRequest(driverAge, vehicleAge, mileage, claims, 500)));
    }

    @ParameterizedTest
    @CsvSource({
            "17,3,12000,0,driver_age", "81,3,12000,0,driver_age",
            "30,-1,12000,0,vehicle_age", "30,21,12000,0,vehicle_age",
            "30,3,-1,0,annual_mileage", "30,3,50001,0,annual_mileage",
            "30,3,12000,-1,prior_claims", "30,3,12000,4,prior_claims"
    })
    void liabilityRejectsOutOfRangeInputs(int driverAge, int vehicleAge, int mileage,
                                        int claims, String field) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.calculateLiability(driverAge, vehicleAge, mileage, claims));
        assertTrue(error.getMessage().contains(field));
        error = assertThrows(IllegalArgumentException.class,
                () -> service.compare(new PricingRequest(driverAge, vehicleAge, mileage, claims, 1000)));
        assertTrue(error.getMessage().contains(field));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 499, 501, 999, 1001, 1999, 2001})
    void componentsRejectUnsupportedDeductibles(int deductible) {
        assertThrows(IllegalArgumentException.class, () -> service.calculateCollision(deductible));
        assertThrows(IllegalArgumentException.class, () -> service.calculateComprehensive(deductible));
        assertThrows(IllegalArgumentException.class,
                () -> service.compare(new PricingRequest(30, 3, 12000, 0, deductible)));
    }

    @Test
    void rejectsNullRequest() {
        assertThrows(IllegalArgumentException.class, () -> service.compare(null));
    }

    @ParameterizedTest
    @MethodSource("requestsWithMissingValues")
    void rejectsEveryMissingRequiredValue(PricingRequest request) {
        assertThrows(IllegalArgumentException.class, () -> service.compare(request));
    }

    static Stream<PricingRequest> requestsWithMissingValues() {
        return Stream.of(new PricingRequest(null, 3, 12000, 0, 1000),
                new PricingRequest(30, null, 12000, 0, 1000),
                new PricingRequest(30, 3, null, 0, 1000),
                new PricingRequest(30, 3, 12000, null, 1000),
                new PricingRequest(30, 3, 12000, 0, null));
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(new BigDecimal(expected), actual);
        assertEquals(2, actual.scale());
    }

    private static void assertComponentTotals(PricingResponse result) {
        for (PackageBreakdown breakdown : new PackageBreakdown[]{
                result.basic(), result.standard(), result.enhanced()}) {
            assertEquals(breakdown.liability().add(breakdown.collision()).add(breakdown.comprehensive()),
                    breakdown.annualTotal());
            assertEquals(result.basic().liability(), breakdown.liability());
            assertEquals(2, breakdown.liability().scale());
            assertEquals(2, breakdown.collision().scale());
            assertEquals(2, breakdown.comprehensive().scale());
            assertEquals(2, breakdown.annualTotal().scale());
        }
    }
}
