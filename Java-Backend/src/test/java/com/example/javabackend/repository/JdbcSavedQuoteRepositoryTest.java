package com.example.javabackend.repository;

import com.example.javabackend.dto.PricingRequest;
import com.example.javabackend.dto.PricingResponse;
import com.example.javabackend.dto.SaveQuoteRequest;
import com.example.javabackend.enums.PackageType;
import com.example.javabackend.repository.JdbcSavedQuoteRepository.Row;
import com.example.javabackend.service.PricingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// T-09 tests for JdbcSavedQuoteRepository.
// @Transactional rolls back everything each test writes (test data included), so real data is never changed.
@SpringBootTest
@Transactional
@Sql("/saved-quote-fixtures.sql")
class JdbcSavedQuoteRepositoryTest {

    @Autowired
    private JdbcSavedQuoteRepository repository;

    @Autowired
    private PricingService pricingService;

    @Autowired
    private JdbcTemplate jdbc;

    private long alice;
    private long bob;
    private long trainingRateId;

    @BeforeEach
    void findIds() {
        alice = repository.findCustomerIdByUsername("t09.alice.test").orElseThrow();
        bob = repository.findCustomerIdByUsername("t09.bob.test").orElseThrow();
        trainingRateId = repository.findRateConfigurationId("TRAINING-1").orElseThrow();
    }

    @Test
    void insertSavesQuoteAndReturnsIt() {
        SaveQuoteRequest request = newRequest();

        Row saved = insert(alice, request).orElseThrow();

        assertTrue(saved.quoteReference().startsWith("Q-"));
        assertEquals(alice, saved.customerId());
        assertEquals(request.saveRequestKey(), saved.requestKey());
        assertEquals("My quote", saved.quoteName());
        assertEquals(PackageType.STANDARD, saved.selectedPackage());
        assertEquals(benchmarkInputs(), saved.pricingInputs());

        assertEquals(new BigDecimal("702.00"), column("basic_total", saved.id()));
        assertEquals(new BigDecimal("906.00"), column("standard_total", saved.id()));
        assertEquals(new BigDecimal("1008.00"), column("enhanced_total", saved.id()));
    }

    @Test
    void insertingTwiceWithSameRequestKeyOnlyCreatesOneQuote() {
        SaveQuoteRequest request = newRequest();

        Row first = insert(alice, request).orElseThrow();
        Optional<Row> second = insert(alice, request);

        assertTrue(second.isEmpty());
        assertEquals(first, repository.findByCustomerAndKey(alice, request.saveRequestKey()).orElseThrow());
        assertEquals(4, repository.findAllOwned(alice).size());
    }

    @Test
    void listShowsOnlyMyQuotesNewestFirst() {
        List<Row> aliceQuotes = repository.findAllOwned(alice);
        List<Row> bobQuotes = repository.findAllOwned(bob);

        assertEquals(3, aliceQuotes.size());
        assertEquals("Q-T09A03", aliceQuotes.get(0).quoteReference());
        assertEquals("Q-T09A02", aliceQuotes.get(1).quoteReference());
        assertEquals("Q-T09A01", aliceQuotes.get(2).quoteReference());

        assertEquals(1, bobQuotes.size());
        assertEquals("Q-T09B01", bobQuotes.get(0).quoteReference());
    }

    @Test
    void customerCannotOpenAnotherCustomersQuote() {
        long aliceQuoteId = quoteId("Q-T09A01");

        assertTrue(repository.findOwnedById(alice, aliceQuoteId).isPresent());
        assertTrue(repository.findOwnedById(bob, aliceQuoteId).isEmpty());
        assertTrue(repository.findOwnedById(alice, -1).isEmpty());
    }

    @Test
    void findByCustomerAndKeyOnlyFindsMyOwnKey() {
        UUID aliceKey = UUID.fromString("00000000-0000-0000-0000-00000000a001");

        assertEquals("Q-T09A01", repository.findByCustomerAndKey(alice, aliceKey).orElseThrow().quoteReference());
        assertTrue(repository.findByCustomerAndKey(bob, aliceKey).isEmpty());
    }

    @Test
    void unknownCustomerAndRateVersionAreNotFound() {
        assertTrue(repository.findCustomerIdByUsername("nobody").isEmpty());
        assertTrue(repository.findRateConfigurationId("NOT-A-REAL-VERSION").isEmpty());
    }

    @Test
    void databaseBlocksChangingOrDeletingASavedQuote() {
        // In Postgres a failed statement cancels the whole transaction, so each attempt gets a savepoint to go back to
        jdbc.execute("SAVEPOINT before_update");
        assertThrows(DataAccessException.class,
                () -> jdbc.update("UPDATE saved_quote SET quote_name = 'Changed' WHERE quote_reference = 'Q-T09A01'"));
        jdbc.execute("ROLLBACK TO SAVEPOINT before_update");

        jdbc.execute("SAVEPOINT before_delete");
        assertThrows(DataAccessException.class,
                () -> jdbc.update("DELETE FROM saved_quote WHERE quote_reference = 'Q-T09A01'"));
        jdbc.execute("ROLLBACK TO SAVEPOINT before_delete");

        assertEquals("Alice first quote", repository.findOwnedById(alice, quoteId("Q-T09A01")).orElseThrow().quoteName());
    }

    private PricingRequest benchmarkInputs() {
        return new PricingRequest(30, 3, 12000, 0, 1000);
    }

    private SaveQuoteRequest newRequest() {
        return new SaveQuoteRequest("My quote", UUID.randomUUID(), PackageType.STANDARD, benchmarkInputs());
    }

    private Optional<Row> insert(long customerId, SaveQuoteRequest request) {
        PricingResponse comparison = pricingService.compare(request.pricingInputs());
        return repository.insert(customerId, request, trainingRateId, comparison, "{\"note\": \"test\"}");
    }

    private long quoteId(String reference) {
        return jdbc.queryForObject("SELECT id FROM saved_quote WHERE quote_reference = ?", Long.class, reference);
    }

    private BigDecimal column(String column, long quoteId) {
        return jdbc.queryForObject("SELECT " + column + " FROM saved_quote WHERE id = ?", BigDecimal.class, quoteId);
    }
}
