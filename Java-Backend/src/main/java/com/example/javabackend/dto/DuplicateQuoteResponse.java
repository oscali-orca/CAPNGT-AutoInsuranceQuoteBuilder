package com.example.javabackend.dto;

import com.example.javabackend.enums.PackageType;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

// Unsaved draft. No new database row is created when duplicating.
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DuplicateQuoteResponse(
        String quoteName,
        PackageType selectedPackage,
        PricingRequest pricingInputs,
        PricingResponse comparison
) {}
