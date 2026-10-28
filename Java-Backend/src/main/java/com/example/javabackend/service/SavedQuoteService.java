package com.example.javabackend.service;

import com.example.javabackend.dto.DuplicateQuoteResponse;
import com.example.javabackend.dto.PricingResponse;
import com.example.javabackend.dto.SaveQuoteRequest;
import com.example.javabackend.dto.SavedQuoteResponse;
import com.example.javabackend.repository.JdbcSavedQuoteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;

import java.util.List;

@Service
public class SavedQuoteService {
    private static final int MAX_QUOTE_NAME_LENGTH = 100;
    private final PricingService pricing;
    private final JdbcSavedQuoteRepository quotes;
    private final ObjectMapper json;

    public SavedQuoteService(PricingService pricing, JdbcSavedQuoteRepository quotes, ObjectMapper json) {
        this.pricing = pricing;
        this.quotes = quotes;
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

    private SavedQuoteResponse replay(JdbcSavedQuoteRepository.Row row, SaveQuoteRequest request) {
        if (!row.quoteName().equals(request.quoteName()) || row.selectedPackage() != request.selectedPackage()
                || !row.pricingInputs().equals(request.pricingInputs())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Save request key was already used with a different payload");
        }
        return response(row);
    }

    private SavedQuoteResponse response(JdbcSavedQuoteRepository.Row row) {
        SavedQuoteResponse.Snapshot snapshot;
        try {
            snapshot = json.readValue(row.snapshotJson(), SavedQuoteResponse.Snapshot.class);
        } catch (JacksonException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Stored quote snapshot is invalid. Historical prices have not been recalculated.", exception);
        }
        if (snapshot == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Stored quote snapshot is missing. Historical prices have not been recalculated.");
        }
        if (snapshot.comparison() == null) {
            // Repository fixtures may contain only {} or a note. Never invent historical components.
            if (row.rateVersion() == null || row.basicTotal() == null
                    || row.standardTotal() == null || row.enhancedTotal() == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Stored quote totals are missing. Historical prices cannot be recovered without the original data.");
            }
            var totals = new SavedQuoteResponse.StoredTotals(row.rateVersion(), row.basicTotal(),
                    row.standardTotal(), row.enhancedTotal());
            snapshot = new SavedQuoteResponse.Snapshot(snapshot.schemaVersion(), row.pricingInputs(),
                    row.selectedPackage(), null, totals,
                    "Historical breakdown unavailable. Showing stored totals and inputs; prices have not been recalculated.");
        } else if (snapshot.schemaVersion() == null || snapshot.inputs() == null
                || snapshot.selectedPackage() == null) {
            snapshot = new SavedQuoteResponse.Snapshot(snapshot.schemaVersion(),
                    snapshot.inputs() == null ? row.pricingInputs() : snapshot.inputs(),
                    snapshot.selectedPackage() == null ? row.selectedPackage() : snapshot.selectedPackage(),
                    snapshot.comparison(), null,
                    "Historical snapshot metadata is incomplete. Stored prices are shown unchanged.");
        }
        if (snapshot.comparison() != null) {
            var comparison = snapshot.comparison();
            if (comparison.rateVersion() == null || comparison.basic() == null
                    || comparison.standard() == null || comparison.enhanced() == null
                    || comparison.basic().annualTotal() == null
                    || comparison.standard().annualTotal() == null
                    || comparison.enhanced().annualTotal() == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Stored quote comparison is incomplete. Historical prices have not been recalculated.");
            }
        }
        return new SavedQuoteResponse(row.id(), row.quoteReference(), row.quoteName(), row.selectedPackage(),
                row.createdAt(), snapshot);
    }

    private void validate(SaveQuoteRequest request) {
        if (request == null || request.pricingInputs() == null || request.selectedPackage() == null
                || request.saveRequestKey() == null) {
            throw new IllegalArgumentException("pricingInputs, selectedPackage, and saveRequestKey are required");
        }
        if (request.quoteName() == null || request.quoteName().isBlank()
                || request.quoteName().strip().length() > MAX_QUOTE_NAME_LENGTH) {
            throw new IllegalArgumentException("quoteName must contain 1 to 100 characters");
        }
    }

    private long currentCustomerId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        return quotes.findCustomerIdByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required"));
    }
}
