package com.example.javabackend.dto;

import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.example.javabackend.enums.PackageType;

public record SavedQuoteResponse(
        long id,
        String quoteReference,
        String quoteName,
        PackageType selectedPackage,
        Instant createdAt,
        Snapshot snapshot
) {
    // Typed prices deserialize directly to BigDecimal; historical results are never repriced.
    public record Snapshot(
            @JsonAlias("schema_version") int schemaVersion,
            PricingRequest inputs,
            @JsonAlias("selected_package") PackageType selectedPackage,
            PricingResponse comparison
    ) {}
}
