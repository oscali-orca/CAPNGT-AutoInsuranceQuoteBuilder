package com.example.javabackend.dto;

import com.example.javabackend.enums.PackageType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

// T-06: Prices are intentionally absent from the client request.
public record SaveQuoteRequest(
        @NotBlank
        @Size(max = 100)
        String quoteName,
        @NotNull
        UUID saveRequestKey,
        @NotNull
        PackageType selectedPackage,
        @NotNull
        @Valid
        PricingRequest pricingInputs
) {}
