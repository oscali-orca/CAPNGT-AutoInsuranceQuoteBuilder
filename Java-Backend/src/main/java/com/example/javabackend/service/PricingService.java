package com.example.javabackend.service;

import com.example.javabackend.dto.PricingRequest;
import com.example.javabackend.dto.PricingResponse;
import com.example.javabackend.dto.PricingResponse.PackageBreakdown;
import com.example.javabackend.dto.PricingResponse.RiskFactors;
import com.example.javabackend.dto.QuoteConfigResponse;
import com.example.javabackend.enums.PackageType;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PricingService {

    private static final BigDecimal ZERO_PREMIUM = new BigDecimal("0.00");
    private static final Pattern CLAIMS_FORMULA = Pattern.compile(
            "^\\s*([0-9]+(?:\\.[0-9]+)?)\\s*\\+\\s*\\(([0-9]+(?:\\.[0-9]+)?)\\s*\\*\\s*claims_count\\)\\s*$");
    private static final Pattern VEHICLE_AGE_FORMULA = Pattern.compile(
            "^\\s*([0-9]+(?:\\.[0-9]+)?)\\s*\\+\\s*\\(\\(([0-9]+(?:\\.[0-9]+)?)\\s*-\\s*vehicle_age_years\\)\\s*\\*\\s*([0-9]+(?:\\.[0-9]+)?)\\)\\s*$");
    private static final Pattern ROUNDING_POLICY = Pattern.compile("^([A-Z_]+)_([0-9]+)_DECIMALS$");

    private final QuoteConfigService quoteConfigService;

    public PricingService(QuoteConfigService quoteConfigService) {
        this.quoteConfigService = quoteConfigService;
    }

    public PricingResponse compare(PricingRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        QuoteConfigResponse rate = activeConfiguration();
        Map<String, Object> config = rate.config();
        validateRiskInputs(request.driverAge(), request.vehicleAge(),
                request.annualMileage(), request.priorClaims());
        if (request.comparisonDeductible() == null) {
            throw new IllegalArgumentException("comparison_deductible is required");
        }

        int deductible = request.comparisonDeductible();
        BigDecimal deductibleFactor = getDeductibleFactor(config, deductible);
        RiskFactors factors = calculateRiskFactors(request.driverAge(), request.vehicleAge(),
                request.annualMileage(), request.priorClaims(), config);
        BigDecimal liability = calculateLiability(factors, config);
        BigDecimal collision = calculateCollision(config, deductible);
        BigDecimal comprehensive = calculateComprehensive(config, deductible);

        return new PricingResponse(rate.versionCode(), rate.currency(),
                String.valueOf(config.getOrDefault("period", "annual")).toUpperCase(), factors,
                deductible, deductibleFactor,
                packageBreakdown(config, PackageType.BASIC, liability, ZERO_PREMIUM, ZERO_PREMIUM, null),
                packageBreakdown(config, PackageType.STANDARD, liability, collision, ZERO_PREMIUM, deductible),
                packageBreakdown(config, PackageType.ENHANCED, liability, collision, comprehensive, deductible));
    }

    private PackageBreakdown packageBreakdown(Map<String, Object> config, PackageType type, BigDecimal liability,
                                              BigDecimal collision, BigDecimal comprehensive,
                                              Integer deductible) {
        Map<String, Object> packageConfig = objectMap(objectMap(config.get("packages")).get(type.name()));
        return new PackageBreakdown(type, liability, collision, comprehensive,
                booleanValue(packageConfig.get("deductible_applies")), deductible,
                liability.add(collision).add(comprehensive)
                        .setScale(roundingScale(config, "package_total"), roundingMode(config, "package_total")));
    }

    public BigDecimal calculateLiability(
            int driverAge,
            int vehicleAge,
            int annualMileage,
            int priorClaims) {

        validateRiskInputs(driverAge, vehicleAge, annualMileage, priorClaims);
        Map<String, Object> config = activeConfiguration().config();
        return calculateLiability(calculateRiskFactors(driverAge, vehicleAge, annualMileage,
                priorClaims, config), config);
    }

    private RiskFactors calculateRiskFactors(int driverAge, int vehicleAge,
                                             int annualMileage, int priorClaims, Map<String, Object> config) {
        BigDecimal driverFactor = bandFactor(config, "driver_factors", "age_min", "age_max", driverAge);
        BigDecimal mileageFactor = bandFactor(config, "mileage_factors",
                "mileage_min", "mileage_max", annualMileage);
        BigDecimal claimsFactor = formulaFactor(
                String.valueOf(config.get("claims_factor_formula")), CLAIMS_FORMULA, priorClaims);
        BigDecimal vehicleFactor = vehicleAgeFactor(
                String.valueOf(config.get("vehicle_age_formula")), vehicleAge);
        return new RiskFactors(driverFactor, mileageFactor, claimsFactor, vehicleFactor);
    }

    private BigDecimal calculateLiability(RiskFactors factors, Map<String, Object> config) {
        // Multiply exact factors, rounding only the completed liability component.
        return decimal(config.get("base_premium"))
                .multiply(factors.driverFactor())
                .multiply(factors.mileageFactor())
                .multiply(factors.claimsFactor())
                .multiply(factors.vehicleFactor())
                .setScale(roundingScale(config, "liability_premium"), roundingMode(config, "liability_premium"));
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

    private BigDecimal getDeductibleFactor(Map<String, Object> config, int deductible) {
        Object factor = objectMap(config.get("deductible_factors")).get(String.valueOf(deductible));
        if (factor == null) {
            throw new IllegalArgumentException("comparison_deductible must be one of the configured values");
        }
        return decimal(factor);
    }

    public BigDecimal calculateCollision(int deductible) {
        return calculateCollision(activeConfiguration().config(), deductible);
    }

    public BigDecimal calculateComprehensive(int deductible) {
        return calculateComprehensive(activeConfiguration().config(), deductible);
    }

    private BigDecimal calculateCollision(Map<String, Object> config, int deductible) {
        BigDecimal base = decimal(objectMap(objectMap(config.get("packages")).get("STANDARD"))
                .get("collision_cost"));
        return base.multiply(getDeductibleFactor(config, deductible))
                .setScale(roundingScale(config, "addon"), roundingMode(config, "addon"));
    }

    private BigDecimal calculateComprehensive(Map<String, Object> config, int deductible) {
        BigDecimal base = decimal(objectMap(objectMap(config.get("packages")).get("ENHANCED"))
                .get("comprehensive_cost"));
        return base.multiply(getDeductibleFactor(config, deductible))
                .setScale(roundingScale(config, "addon"), roundingMode(config, "addon"));
    }

    private BigDecimal bandFactor(Map<String, Object> config, String key, String minKey,
                                  String maxKey, int value) {
        Object factorBands = config.get(key);
        if (!(factorBands instanceof List<?> bands)) {
            throw new IllegalStateException("Active rate configuration is missing " + key);
        }
        return bands.stream().map(this::objectMap)
                .filter(band -> value >= decimal(band.get(minKey)).intValue()
                        && value <= decimal(band.get(maxKey)).intValue())
                .map(band -> decimal(band.get("factor")))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No configured pricing factor for value " + value));
    }

    private BigDecimal formulaFactor(String formula, Pattern pattern, int input) {
        Matcher matcher = pattern.matcher(formula);
        if (!matcher.matches()) {
            throw new IllegalStateException("Active rate configuration has an unsupported claims formula");
        }
        return new BigDecimal(matcher.group(1)).add(
                new BigDecimal(matcher.group(2)).multiply(BigDecimal.valueOf(input)));
    }

    private BigDecimal vehicleAgeFactor(String formula, int vehicleAge) {
        Matcher matcher = VEHICLE_AGE_FORMULA.matcher(formula);
        if (!matcher.matches()) {
            throw new IllegalStateException("Active rate configuration has an unsupported vehicle age formula");
        }
        return new BigDecimal(matcher.group(1)).add(
                new BigDecimal(matcher.group(2)).subtract(BigDecimal.valueOf(vehicleAge))
                        .multiply(new BigDecimal(matcher.group(3))));
    }

    private QuoteConfigResponse activeConfiguration() {
        return quoteConfigService.findActiveConfiguration()
                .orElseThrow(() -> new IllegalStateException("No active rate configuration is available"));
    }

    private Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalStateException("Active rate configuration contains an invalid object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) map;
        return result;
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value instanceof String string) {
            return new BigDecimal(string);
        }
        throw new IllegalStateException("Active rate configuration contains a missing or invalid number");
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        throw new IllegalStateException("Active rate configuration contains an invalid boolean");
    }

    private int roundingScale(Map<String, Object> config, String component) {
        Matcher matcher = ROUNDING_POLICY.matcher(String.valueOf(objectMap(config.get("rounding")).get(component)));
        if (!matcher.matches()) {
            throw new IllegalStateException("Active rate configuration contains an unsupported rounding policy");
        }
        return Integer.parseInt(matcher.group(2));
    }

    private RoundingMode roundingMode(Map<String, Object> config, String component) {
        Matcher matcher = ROUNDING_POLICY.matcher(String.valueOf(objectMap(config.get("rounding")).get(component)));
        if (!matcher.matches()) {
            throw new IllegalStateException("Active rate configuration contains an unsupported rounding policy");
        }
        return RoundingMode.valueOf(matcher.group(1));
    }
}
