package com.example.javabackend.repository;

import com.example.javabackend.dto.PricingRequest;
import com.example.javabackend.dto.PricingResponse;
import com.example.javabackend.dto.SaveQuoteRequest;
import com.example.javabackend.enums.PackageType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavedQuoteRepository {
    record Row(long id, String quoteReference, long customerId, UUID requestKey,
               String quoteName, PackageType selectedPackage, PricingRequest pricingInputs, String snapshotJson,
               Instant createdAt) {}

    Optional<Row> findByCustomerAndKey(long customerId, UUID requestKey);
    Optional<Row> findOwnedById(long customerId, long quoteId);
    List<Row> findAllOwned(long customerId);
    Optional<Long> findRateConfigurationId(String versionCode);

    // Empty means this customer's request key already exists; no row is updated.
    Optional<Row> insert(long customerId, SaveQuoteRequest request, long rateConfigurationId,
                         PricingResponse comparison, String snapshotJson);
}
