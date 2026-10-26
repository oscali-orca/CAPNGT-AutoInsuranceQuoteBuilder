package com.example.javabackend.service;

import com.example.javabackend.dto.DuplicateQuoteResponse;
import com.example.javabackend.dto.PricingResponse;
import com.example.javabackend.dto.SaveQuoteRequest;
import com.example.javabackend.dto.SavedQuoteResponse;
import com.example.javabackend.repository.SavedQuoteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Service
public class SavedQuoteService {
    private static final int MAX_QUOTE_NAME_LENGTH = 100;
    private final PricingService pricing;
    private final SavedQuoteRepository quotes;
    private final CurrentCustomerIdProvider identity;
    private final ObjectMapper json;

    public SavedQuoteService(PricingService pricing, SavedQuoteRepository quotes,
                             CurrentCustomerIdProvider identity, ObjectMapper json) {
        this.pricing = pricing;
        this.quotes = quotes;
        this.identity = identity;
        this.json = json;
    }

    // A separate statement after ON CONFLICT sees the committed winner's row.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SavedQuoteResponse save(SaveQuoteRequest request) {
        long customerId = currentCustomerId();
        validate(request);
        SaveQuoteRequest normalized = new SaveQuoteRequest(request.quoteName().strip(),
                request.saveRequestKey(), request.selectedPackage(), request.pricingInputs());
        var existing = quotes.findByCustomerAndKey(customerId, normalized.saveRequestKey());
        if (existing.isPresent()) {
            return replay(existing.get(), normalized);
        }

        // Never accept or persist prices supplied by React.
        PricingResponse comparison = pricing.compare(normalized.pricingInputs());
        long rateId = quotes.findRateConfigurationId(comparison.rateVersion())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "The calculated rate version is not configured for saving quotes"));
        var snapshot = new SavedQuoteResponse.Snapshot(1, normalized.pricingInputs(),
                normalized.selectedPackage(), comparison);
        var inserted = quotes.insert(customerId, normalized, rateId, comparison, json.writeValueAsString(snapshot));
        var row = inserted.orElseGet(() -> quotes.findByCustomerAndKey(customerId, normalized.saveRequestKey())
                .orElseThrow(() -> new IllegalStateException("Conflicting saved quote could not be read")));
        return replay(row, normalized);
    }

    @Transactional(readOnly = true)
    public SavedQuoteResponse get(long id) {
        long customerId = currentCustomerId();
        return response(quotes.findOwnedById(customerId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quote not found")));
    }

    @Transactional(readOnly = true)
    public List<SavedQuoteResponse> list() {
        long customerId = currentCustomerId();
        return quotes.findAllOwned(customerId).stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public DuplicateQuoteResponse duplicate(long id) {
        SavedQuoteResponse original = get(id);
        String suffix = " (copy)";
        int end = Math.min(original.quoteName().length(), MAX_QUOTE_NAME_LENGTH - suffix.length());
        if (end > 0 && Character.isHighSurrogate(original.quoteName().charAt(end - 1))) {
            end--;
        }
        return new DuplicateQuoteResponse(original.quoteName().substring(0, end) + suffix,
                original.selectedPackage(), original.snapshot().inputs(),
                pricing.compare(original.snapshot().inputs()));
    }

    private SavedQuoteResponse replay(SavedQuoteRepository.Row row, SaveQuoteRequest request) {
        if (!row.quoteName().equals(request.quoteName()) || row.selectedPackage() != request.selectedPackage()
                || !row.pricingInputs().equals(request.pricingInputs())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Save request key was already used with a different payload");
        }
        return response(row);
    }

    private SavedQuoteResponse response(SavedQuoteRepository.Row row) {
        var snapshot = json.readValue(row.snapshotJson(), SavedQuoteResponse.Snapshot.class);
        return new SavedQuoteResponse(row.id(), row.quoteReference(), row.quoteName(), row.selectedPackage(),
                row.createdAt(), snapshot);
    }

    private void validate(SaveQuoteRequest request) {
        if (request == null || request.pricingInputs() == null || request.selectedPackage() == null
                || request.saveRequestKey() == null) {
            throw new IllegalArgumentException("pricing_inputs, selected_package, and save_request_key are required");
        }
        if (request.quoteName() == null || request.quoteName().isBlank()
                || request.quoteName().strip().length() > MAX_QUOTE_NAME_LENGTH) {
            throw new IllegalArgumentException("quote_name must contain 1 to 100 characters");
        }
    }

    private long currentCustomerId() {
        long customerId = identity.requireCurrentCustomerId();
        if (customerId <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        return customerId;
    }
}
