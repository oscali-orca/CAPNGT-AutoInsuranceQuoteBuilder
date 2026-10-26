package com.example.javabackend.dto;

import java.time.Instant;
import com.example.javabackend.enums.PackageType;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SavedQuoteResponse(
        long id,
        String quoteReference,
        String quoteName,
        PackageType selectedPackage,
        Instant createdAt,
        Snapshot snapshot
) {
    // Typed prices deserialize directly to BigDecimal; historical results are never repriced.
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Snapshot(
            int schemaVersion,
            PricingRequest inputs,
            PackageType selectedPackage,
            PricingResponse comparison
    ) {}
}
