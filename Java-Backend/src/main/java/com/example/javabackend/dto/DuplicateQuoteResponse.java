package com.example.javabackend.dto;

import com.example.javabackend.enums.PackageType;

// Unsaved draft. No new database row is created when duplicating.
public record DuplicateQuoteResponse(
        String quoteName,
        PackageType selectedPackage,
        PricingRequest pricingInputs,
        PricingResponse comparison
) {}
