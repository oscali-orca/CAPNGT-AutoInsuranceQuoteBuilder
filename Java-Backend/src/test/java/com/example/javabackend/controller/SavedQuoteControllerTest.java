package com.example.javabackend.controller;

import com.example.javabackend.dto.*;
import com.example.javabackend.enums.PackageType;
import com.example.javabackend.repository.SavedQuoteRepository;
import com.example.javabackend.service.PricingService;
import com.example.javabackend.service.SavedQuoteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SavedQuoteControllerTest {
    private final JsonMapper json = JsonMapper.builder().build();
    private SavedQuoteRepository repository;
    private MockMvc mvc;
    private static final String INPUT = """
            {"quote_name":"Commute","save_request_key":"15c8b4ac-7b5a-49b5-b020-64bf35cab72e",
             "selected_package":"STANDARD","pricing_inputs":{"driver_age":30,"vehicle_age":3,
             "annual_mileage":12000,"prior_claims":0,"comparison_deductible":1000}}
            """;

    @BeforeEach
    void setUp() {
        repository = mock(SavedQuoteRepository.class);
        when(repository.findByCustomerAndKey(anyLong(), any())).thenReturn(Optional.empty());
        when(repository.findOwnedById(anyLong(), anyLong())).thenReturn(Optional.empty());
        when(repository.findRateConfigurationId("TRAINING-1")).thenReturn(Optional.of(7L));
        when(repository.insert(anyLong(), any(), anyLong(), any(), anyString())).thenAnswer(invocation -> {
            SaveQuoteRequest request = invocation.getArgument(1);
            return Optional.of(new SavedQuoteRepository.Row(42, "Q-000000000000000042",
                    invocation.getArgument(0), request.saveRequestKey(), request.quoteName(),
                    request.selectedPackage(), request.pricingInputs(), invocation.getArgument(4),
                    Instant.parse("2026-10-26T12:00:00Z")));
        });
        mvc = controllerFor(31);
    }

    private MockMvc controllerFor(long customerId) {
        // This provider exists only in the test; no production identity bean is installed.
        var service = new SavedQuoteService(new PricingService(), repository, () -> customerId, json);
        return MockMvcBuilders.standaloneSetup(new SavedQuoteController(service))
                .setMessageConverters(new JacksonJsonHttpMessageConverter(json)).build();
    }

    @Test
    void savesSnakeCaseRequestUsingServerIdentityAndReturnsNumericIdAndExactSnapshot() throws Exception {
        String body = mvc.perform(post("/api/quotes").contentType(MediaType.APPLICATION_JSON).content(INPUT))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        SavedQuoteResponse result = json.readValue(body, SavedQuoteResponse.class);
        assertEquals(42L, result.id());
        assertEquals(PackageType.STANDARD, result.selectedPackage());
        assertEquals("906.00", result.snapshot().comparison().standard().annualTotal().toPlainString());
        assertTrue(json.readTree(body).has("quote_reference"));
        assertFalse(json.readTree(body).has("customer_id"));
        verify(repository).insert(eq(31L), any(), eq(7L), any(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"quote_name", "save_request_key", "selected_package", "pricing_inputs"})
    void rejectsMissingAndNullEnvelopeFields(String field) throws Exception {
        ObjectNode input = (ObjectNode) json.readTree(INPUT);
        input.remove(field);
        assertBadRequest(input.toString());
        input.putNull(field);
        assertBadRequest(input.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"driver_age", "vehicle_age", "annual_mileage", "prior_claims", "comparison_deductible"})
    void invalidAndMissingNestedPricingFieldsProduce400(String field) throws Exception {
        ObjectNode input = (ObjectNode) json.readTree(INPUT);
        ObjectNode risk = (ObjectNode) input.get("pricing_inputs");
        risk.put(field, -1);
        assertBadRequest(input.toString());
        risk.remove(field);
        assertBadRequest(input.toString());
        risk.put(field, 30.5);
        assertBadRequest(input.toString());
        risk.put(field, "30");
        assertBadRequest(input.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{", "null", "{}", "[]"})
    void malformedBodiesProduceSafe400(String input) throws Exception {
        assertBadRequest(input);
    }

    @Test
    void validatesUuidPackageAndNameLimit() throws Exception {
        ObjectNode input = (ObjectNode) json.readTree(INPUT);
        input.put("save_request_key", "not-a-uuid");
        assertBadRequest(input.toString());
        input = (ObjectNode) json.readTree(INPUT);
        input.put("selected_package", "PREMIUM");
        assertBadRequest(input.toString());
        input = (ObjectNode) json.readTree(INPUT);
        input.put("quote_name", "x".repeat(101));
        assertBadRequest(input.toString());
    }

    @Test
    void unknownOwnedQuoteReturns404AndInvalidIdReturns400() throws Exception {
        mvc.perform(get("/api/quotes/42")).andExpect(status().isNotFound());
        mvc.perform(post("/api/quotes/42/duplicate")).andExpect(status().isNotFound());
        mvc.perform(get("/api/quotes/not-a-number")).andExpect(status().isBadRequest());
        verify(repository, times(2)).findOwnedById(31, 42);
    }

    @Test
    void rejectedServerIdentityReturns401() throws Exception {
        controllerFor(0).perform(get("/api/quotes")).andExpect(status().isUnauthorized());
        verifyNoInteractions(repository);
    }

    @Test
    void changedPayloadForExistingKeyProduces409() throws Exception {
        UUID key = json.readValue(INPUT, SaveQuoteRequest.class).saveRequestKey();
        when(repository.findByCustomerAndKey(31, key)).thenReturn(Optional.of(new SavedQuoteRepository.Row(
                42, "Q-42", 31, key, "Different", PackageType.STANDARD,
                new PricingRequest(30, 3, 12000, 0, 1000), "{}", Instant.EPOCH)));
        mvc.perform(post("/api/quotes").contentType(MediaType.APPLICATION_JSON).content(INPUT))
                .andExpect(status().isConflict());
        verify(repository, never()).insert(anyLong(), any(), anyLong(), any(), anyString());
    }

    @Test
    void exposesNoUpdateOrDeleteEndpoint() throws Exception {
        mvc.perform(put("/api/quotes/42").contentType(MediaType.APPLICATION_JSON).content(INPUT))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(delete("/api/quotes/42")).andExpect(status().isMethodNotAllowed());
    }

    private void assertBadRequest(String input) throws Exception {
        String body = mvc.perform(post("/api/quotes").contentType(MediaType.APPLICATION_JSON).content(input))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertEquals(400, json.readTree(body).get("status").asInt());
        assertTrue(json.readTree(body).get("detail").isString());
        assertFalse(body.contains("com.example"));
        assertFalse(body.contains("tools.jackson"));
        assertFalse(json.readTree(body).has("trace"));
    }
}
