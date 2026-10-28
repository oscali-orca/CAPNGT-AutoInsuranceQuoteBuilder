package com.example.javabackend.dto;

import java.time.Instant;
import java.math.BigDecimal;
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
            @JsonAlias("schema_version") Integer schemaVersion,
            PricingRequest inputs,
            @JsonAlias("selected_package") PackageType selectedPackage,
            PricingResponse comparison,
            StoredTotals storedTotals,
            String warning
    ) {
        public Snapshot(Integer schemaVersion, PricingRequest inputs,
                        PackageType selectedPackage, PricingResponse comparison) {
            this(schemaVersion, inputs, selectedPackage, comparison, null, null);
        }
    }

    // Used only when historical JSON lacks a breakdown; all amounts come from stored columns.
    public record StoredTotals(String rateVersion, BigDecimal basicTotal,
                               BigDecimal standardTotal, BigDecimal enhancedTotal) {}
}
