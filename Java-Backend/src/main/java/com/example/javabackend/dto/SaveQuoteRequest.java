package com.example.javabackend.dto;

import com.example.javabackend.enums.PackageType;
import com.example.javabackend.validation.AllowedIntegerValues;
import com.example.javabackend.validation.IntegerRange;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SaveQuoteRequest(
        @NotNull @IntegerRange(min = 18, max = 80)
        @JsonDeserialize(using = PricingRequest.StrictIntegerDeserializer.class) Integer driverAge,
        @NotNull @IntegerRange(min = 0, max = 20)
        @JsonDeserialize(using = PricingRequest.StrictIntegerDeserializer.class) Integer vehicleAge,
        @NotNull @IntegerRange(min = 0, max = 50000)
        @JsonDeserialize(using = PricingRequest.StrictIntegerDeserializer.class) Integer annualMileage,
        @NotNull @IntegerRange(min = 0, max = 3)
        @JsonDeserialize(using = PricingRequest.StrictIntegerDeserializer.class) Integer priorClaims,
        @NotNull PackageType selectedPackage,
        @NotNull @AllowedIntegerValues({500, 1000, 2000})
        @JsonDeserialize(using = PricingRequest.StrictIntegerDeserializer.class) Integer comparisonDeductible,
        @NotBlank @Size(max = 80) String quoteName,
        @NotNull UUID requestKey
) {
}
