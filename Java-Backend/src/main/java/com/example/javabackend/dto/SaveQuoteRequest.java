package com.example.javabackend.dto;

import com.example.javabackend.enums.PackageType;
import java.util.UUID;

// T-06: Prices are intentionally absent from the client request.
public record SaveQuoteRequest(
        String quoteName,
        UUID saveRequestKey,
        PackageType selectedPackage,
        PricingRequest pricingInputs
) {}
