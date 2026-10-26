package com.example.javabackend.service;

import com.example.javabackend.dto.*;
import com.example.javabackend.enums.PackageType;
import com.example.javabackend.repository.SavedQuoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class SavedQuoteServiceTest {
    private static final PricingRequest INPUTS = new PricingRequest(30, 3, 12000, 0, 1000);
    private final JsonMapper json = JsonMapper.builder().build();
    private final MemoryRepository repository = new MemoryRepository();
    private final TrackingPricing pricing = new TrackingPricing();
    private final AtomicLong customer = new AtomicLong(1);
    private SavedQuoteService service;

    @BeforeEach
    void setUp() {
        service = new SavedQuoteService(pricing, repository, customer::get, json);
    }

    @Test
    void savesBackendBenchmarkWithAllPackagesInputsVersionAndReference() {
        SavedQuoteResponse result = service.save(request(" Commute ", UUID.randomUUID(), INPUTS));
        assertEquals("Commute", result.quoteName());
        assertNotNull(result.quoteReference());
        assertEquals(PackageType.STANDARD, result.selectedPackage());
        assertEquals(INPUTS, result.snapshot().inputs());
        assertEquals(1, result.snapshot().schemaVersion());
        assertEquals("TRAINING-1", result.snapshot().comparison().rateVersion());
        assertEquals(new BigDecimal("702.00"), result.snapshot().comparison().basic().annualTotal());
        assertEquals(new BigDecimal("906.00"), result.snapshot().comparison().standard().annualTotal());
        assertEquals(new BigDecimal("1008.00"), result.snapshot().comparison().enhanced().annualTotal());
        assertEquals(1, pricing.calls.get());
        assertEquals(1, repository.rows.size());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsMissingOrBlankNames(String name) {
        assertThrows(IllegalArgumentException.class,
                () -> service.save(request(name, UUID.randomUUID(), INPUTS)));
        assertTrue(repository.rows.isEmpty());
        assertEquals(0, pricing.calls.get());
    }

    @Test
    void accepts100CharacterNamesAndRejects101() {
        assertEquals("x".repeat(100), service.save(request("x".repeat(100), UUID.randomUUID(), INPUTS)).quoteName());
        assertThrows(IllegalArgumentException.class,
                () -> service.save(request("x".repeat(101), UUID.randomUUID(), INPUTS)));
    }

    @Test
    void requiresTheSaveEnvelopeFields() {
        assertThrows(IllegalArgumentException.class, () -> service.save(null));
        assertThrows(IllegalArgumentException.class,
                () -> service.save(new SaveQuoteRequest("Name", null, PackageType.BASIC, INPUTS)));
        assertThrows(IllegalArgumentException.class,
                () -> service.save(new SaveQuoteRequest("Name", UUID.randomUUID(), null, INPUTS)));
        assertThrows(IllegalArgumentException.class,
                () -> service.save(new SaveQuoteRequest("Name", UUID.randomUUID(), PackageType.BASIC, null)));
    }

    @ParameterizedTest
    @MethodSource("invalidPricingInputs")
    void delegatesRiskValidationToPricing(PricingRequest inputs) {
        assertThrows(IllegalArgumentException.class,
                () -> service.save(request("Name", UUID.randomUUID(), inputs)));
        assertTrue(repository.rows.isEmpty());
    }

    static Stream<PricingRequest> invalidPricingInputs() {
        return Stream.of(new PricingRequest(17, 3, 12000, 0, 1000),
                new PricingRequest(81, 3, 12000, 0, 1000), new PricingRequest(30, -1, 12000, 0, 1000),
                new PricingRequest(30, 21, 12000, 0, 1000), new PricingRequest(30, 3, -1, 0, 1000),
                new PricingRequest(30, 3, 50001, 0, 1000), new PricingRequest(30, 3, 12000, -1, 1000),
                new PricingRequest(30, 3, 12000, 4, 1000), new PricingRequest(30, 3, 12000, 0, 750),
                new PricingRequest(null, 3, 12000, 0, 1000), new PricingRequest(30, null, 12000, 0, 1000),
                new PricingRequest(30, 3, null, 0, 1000), new PricingRequest(30, 3, 12000, null, 1000),
                new PricingRequest(30, 3, 12000, 0, null));
    }

    @Test
    void normalizedRetryReturnsOriginalWithoutRepricingEvenIfRatesBecomeUnavailable() {
        UUID key = UUID.randomUUID();
        SavedQuoteResponse first = service.save(request(" Commute ", key, INPUTS));
        repository.rateAvailable = false;
        pricing.override = currentComparison("CURRENT-TEST");
        assertEquals(first, service.save(request("Commute", key, INPUTS)));
        assertEquals(1, pricing.calls.get());
        assertEquals(1, repository.rows.size());
    }

    @ParameterizedTest
    @MethodSource("changedPayloads")
    void sameKeyWithAnyChangedFieldReturns409(SaveQuoteRequest changed) {
        UUID key = changed.saveRequestKey();
        service.save(request("Name", key, INPUTS));
        assertStatus(409, () -> service.save(changed));
        assertEquals(1, repository.rows.size());
        assertEquals(1, pricing.calls.get());
    }

    static Stream<SaveQuoteRequest> changedPayloads() {
        UUID key = UUID.randomUUID();
        return Stream.of(request("Changed", key, INPUTS),
                new SaveQuoteRequest("Name", key, PackageType.ENHANCED, INPUTS),
                request("Name", key, new PricingRequest(31, 3, 12000, 0, 1000)),
                request("Name", key, new PricingRequest(30, 4, 12000, 0, 1000)),
                request("Name", key, new PricingRequest(30, 3, 12001, 0, 1000)),
                request("Name", key, new PricingRequest(30, 3, 12000, 1, 1000)),
                request("Name", key, new PricingRequest(30, 3, 12000, 0, 500)));
    }

    @Test
    void concurrentIdenticalSavesResolveToOneQuote() throws Exception {
        repository.lookupBarrier = new CyclicBarrier(2);
        SaveQuoteRequest request = request("Name", UUID.randomUUID(), INPUTS);
        try (ExecutorService threads = Executors.newFixedThreadPool(2)) {
            Future<SavedQuoteResponse> first = threads.submit(() -> service.save(request));
            Future<SavedQuoteResponse> second = threads.submit(() -> service.save(request));
            assertEquals(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
        }
        assertEquals(1, repository.rows.size());
        assertEquals(2, pricing.calls.get());
    }

    @Test
    void concurrentDifferentPayloadsYieldOneSaveAndOneConflict() throws Exception {
        repository.lookupBarrier = new CyclicBarrier(2);
        UUID key = UUID.randomUUID();
        try (ExecutorService threads = Executors.newFixedThreadPool(2)) {
            Future<Object> first = threads.submit(() -> outcome(request("First", key, INPUTS)));
            Future<Object> second = threads.submit(() -> outcome(request("Second", key, INPUTS)));
            List<Object> results = List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter(SavedQuoteResponse.class::isInstance).count());
            ResponseStatusException conflict = (ResponseStatusException) results.stream()
                    .filter(ResponseStatusException.class::isInstance).findFirst().orElseThrow();
            assertEquals(409, conflict.getStatusCode().value());
        }
        assertEquals(1, repository.rows.size());
    }

    @Test
    void customersCannotReadListOrDuplicateEachOthersQuotesAndKeysArePerCustomer() {
        UUID key = UUID.randomUUID();
        SavedQuoteResponse first = service.save(request("First", key, INPUTS));
        customer.set(2);
        assertStatus(404, () -> service.get(first.id()));
        assertStatus(404, () -> service.duplicate(first.id()));
        assertTrue(service.list().isEmpty());
        SavedQuoteResponse second = service.save(request("Second", key, INPUTS));
        assertNotEquals(first.id(), second.id());
        assertEquals(List.of(second), service.list());
        customer.set(1);
        assertEquals(List.of(first), service.list());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void refusesInvalidServerIdentity(long id) {
        customer.set(id);
        assertStatus(401, () -> service.save(request("Name", UUID.randomUUID(), INPUTS)));
        assertStatus(401, () -> service.get(1));
        assertStatus(401, () -> service.list());
        assertStatus(401, () -> service.duplicate(1));
        assertTrue(repository.rows.isEmpty());
    }

    @Test
    void retrievalAndListingKeepHistoricalPricesButDuplicateUsesCurrentServiceWithoutSaving() {
        SavedQuoteResponse saved = service.save(request("x".repeat(100), UUID.randomUUID(), INPUTS));
        pricing.override = currentComparison("CURRENT-TEST");
        assertEquals(saved, service.get(saved.id()));
        assertEquals(List.of(saved), service.list());
        assertEquals(1, pricing.calls.get());
        DuplicateQuoteResponse draft = service.duplicate(saved.id());
        assertTrue(draft.quoteName().endsWith(" (copy)"));
        assertEquals(100, draft.quoteName().length());
        assertEquals(INPUTS, draft.pricingInputs());
        assertEquals(PackageType.STANDARD, draft.selectedPackage());
        assertEquals("CURRENT-TEST", draft.comparison().rateVersion());
        assertEquals(2, pricing.calls.get());
        assertEquals(1, repository.rows.size());
        assertEquals(saved, service.get(saved.id()));
    }

    @Test
    void snapshotRoundTripPreservesExactDecimalsIncludingHighPrecisionMetadata() {
        SavedQuoteResponse saved = service.save(request("Name", UUID.randomUUID(), INPUTS));
        PricingResponse original = saved.snapshot().comparison();
        BigDecimal exact = new BigDecimal("1.12345678901234567890123456789");
        PricingResponse precise = new PricingResponse(original.rateVersion(), original.currency(), original.period(),
                new PricingResponse.RiskFactors(exact, original.riskFactors().mileageFactor(),
                        original.riskFactors().claimsFactor(), original.riskFactors().vehicleFactor()),
                original.comparisonDeductible(), original.deductibleFactor(),
                original.basic(), original.standard(), original.enhanced());
        var snapshot = new SavedQuoteResponse.Snapshot(1, INPUTS, saved.selectedPackage(), precise);
        var key = repository.rows.keySet().iterator().next();
        var row = repository.rows.get(key);
        repository.rows.put(key, new SavedQuoteRepository.Row(row.id(), row.quoteReference(), row.customerId(),
                row.requestKey(), row.quoteName(), row.selectedPackage(), row.pricingInputs(),
                json.writeValueAsString(snapshot), row.createdAt()));
        SavedQuoteResponse result = service.get(saved.id());
        assertEquals(exact, result.snapshot().comparison().riskFactors().driverFactor());
        assertEquals(new BigDecimal("906.00"), result.snapshot().comparison().standard().annualTotal());
        assertEquals(result, json.readValue(json.writeValueAsString(result), SavedQuoteResponse.class));
        assertEquals(1, pricing.calls.get());
    }

    @Test
    void missingRateVersionReturns503WithoutInserting() {
        repository.rateAvailable = false;
        assertStatus(503, () -> service.save(request("Name", UUID.randomUUID(), INPUTS)));
        assertTrue(repository.rows.isEmpty());
    }

    @Test
    void unrelatedDatabaseFailuresAreNotConvertedTo409() {
        var failure = new DataIntegrityViolationException("Unrelated foreign key failure");
        repository.insertFailure = failure;
        assertSame(failure, assertThrows(DataIntegrityViolationException.class,
                () -> service.save(request("Name", UUID.randomUUID(), INPUTS))));
    }

    private Object outcome(SaveQuoteRequest request) {
        try { return service.save(request); }
        catch (ResponseStatusException exception) { return exception; }
    }

    private static void assertStatus(int expected, org.junit.jupiter.api.function.Executable action) {
        assertEquals(expected, assertThrows(ResponseStatusException.class, action).getStatusCode().value());
    }

    private static SaveQuoteRequest request(String name, UUID key, PricingRequest inputs) {
        return new SaveQuoteRequest(name, key, PackageType.STANDARD, inputs);
    }

    private static PricingResponse currentComparison(String version) {
        PricingResponse original = new PricingService().compare(INPUTS);
        return new PricingResponse(version, original.currency(), original.period(), original.riskFactors(),
                original.comparisonDeductible(), original.deductibleFactor(),
                original.basic(), original.standard(), original.enhanced());
    }

    private static final class TrackingPricing extends PricingService {
        final AtomicInteger calls = new AtomicInteger();
        volatile PricingResponse override;
        @Override public PricingResponse compare(PricingRequest request) {
            calls.incrementAndGet();
            return override == null ? super.compare(request) : override;
        }
    }

    // Models only the repository's atomic-insert contract, not PostgreSQL transaction behavior.
    private static final class MemoryRepository implements SavedQuoteRepository {
        record Key(long customerId, UUID requestKey) {}
        final ConcurrentMap<Key, Row> rows = new ConcurrentHashMap<>();
        final AtomicLong nextId = new AtomicLong();
        volatile CyclicBarrier lookupBarrier;
        boolean rateAvailable = true;
        RuntimeException insertFailure;

        @Override public Optional<Row> findByCustomerAndKey(long customerId, UUID requestKey) {
            Row row = rows.get(new Key(customerId, requestKey));
            if (row == null && lookupBarrier != null) {
                try { lookupBarrier.await(5, TimeUnit.SECONDS); }
                catch (Exception exception) { throw new IllegalStateException(exception); }
            }
            return Optional.ofNullable(row);
        }
        @Override public Optional<Row> findOwnedById(long customerId, long quoteId) {
            return rows.values().stream().filter(row -> row.customerId() == customerId && row.id() == quoteId).findFirst();
        }
        @Override public List<Row> findAllOwned(long customerId) {
            return rows.values().stream().filter(row -> row.customerId() == customerId).toList();
        }
        @Override public Optional<Long> findRateConfigurationId(String versionCode) {
            assertEquals("TRAINING-1", versionCode);
            return rateAvailable ? Optional.of(7L) : Optional.empty();
        }
        @Override public Optional<Row> insert(long customerId, SaveQuoteRequest request, long rateId,
                                              PricingResponse comparison, String snapshotJson) {
            if (insertFailure != null) throw insertFailure;
            assertEquals(7L, rateId);
            long id = nextId.incrementAndGet();
            Row row = new Row(id, "Q-" + id, customerId, request.saveRequestKey(), request.quoteName(),
                    request.selectedPackage(), request.pricingInputs(), snapshotJson, Instant.parse("2026-10-26T12:00:00Z"));
            return rows.putIfAbsent(new Key(customerId, request.saveRequestKey()), row) == null
                    ? Optional.of(row) : Optional.empty();
        }
    }
}
