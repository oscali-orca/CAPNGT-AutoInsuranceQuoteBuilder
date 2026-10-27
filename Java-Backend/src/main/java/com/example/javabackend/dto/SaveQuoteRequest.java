package com.example.javabackend.dto;

import com.example.javabackend.enums.PackageType;
import java.util.UUID;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

// T-06: Prices are intentionally absent from the client request.
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SaveQuoteRequest(
        String quoteName,
        UUID saveRequestKey,
        PackageType selectedPackage,
        PricingRequest pricingInputs
) {}
