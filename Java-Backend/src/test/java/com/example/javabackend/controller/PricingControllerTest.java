package com.example.javabackend.controller;

import com.example.javabackend.service.PricingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PricingControllerTest {
    private static final String PATH = "/api/pricing/compare";
    private static final String INPUT = """
            {"driver_age":30,"vehicle_age":3,"annual_mileage":12000,
             "prior_claims":0,"comparison_deductible":1000}
            """;
    private static final String[] FIELDS = {
            "driver_age", "vehicle_age", "annual_mileage", "prior_claims", "comparison_deductible"
    };
    private final JsonMapper mapper = JsonMapper.builder().build();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new PricingController(new PricingService()))
                .setMessageConverters(new JacksonJsonHttpMessageConverter(mapper)).build();
    }

    @Test
    void postsSnakeCaseComparisonAndSerializesAllBreakdowns() throws Exception {
        String body = mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(INPUT))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode response = mapper.readTree(body);
        assertEquals("TRAINING-1", response.get("rate_version").asString());
        assertEquals("USD", response.get("currency").asString());
        assertEquals("ANNUAL", response.get("period").asString());
        assertEquals(1000, response.get("comparison_deductible").asInt());
        assertDecimal("0.85", response.get("deductible_factor"));
        assertDecimal("1.00", response.get("risk_factors").get("driver_factor"));
        assertDecimal("1.00", response.get("risk_factors").get("mileage_factor"));
        assertDecimal("1.00", response.get("risk_factors").get("claims_factor"));
        assertDecimal("1.17", response.get("risk_factors").get("vehicle_factor"));
        assertDecimal("702.00", response.get("basic").get("annual_total"));
        assertDecimal("906.00", response.get("standard").get("annual_total"));
        assertDecimal("1008.00", response.get("enhanced").get("annual_total"));
        assertEquals("BASIC", response.get("basic").get("package_type").asString());
        assertTrue(response.get("basic").get("deductible").isNull());
        assertFalse(response.get("basic").get("deductible_applicable").asBoolean());
        assertDecimal("204.00", response.get("standard").get("collision"));
        assertDecimal("102.00", response.get("enhanced").get("comprehensive"));
        assertTrue(response.get("enhanced").get("deductible_applicable").asBoolean());
        assertFalse(response.has("rateVersion"));
        assertFalse(response.get("risk_factors").has("driverFactor"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"driver_age", "vehicle_age", "annual_mileage", "prior_claims", "comparison_deductible"})
    void rejectsMissingAndNullRequiredFields(String field) throws Exception {
        ObjectNode input = (ObjectNode) mapper.readTree(INPUT);
        input.remove(field);
        assertBadRequest(input.toString(), field);
        input.putNull(field);
        assertBadRequest(input.toString(), field);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "50001", "30.5", "\"30\"", "true", "[]", "{}", "2147483648"})
    void rejectsInvalidValuesForEveryField(String invalidJson) throws Exception {
        for (String field : FIELDS) {
            ObjectNode input = (ObjectNode) mapper.readTree(INPUT);
            input.set(field, mapper.readTree(invalidJson));
            assertBadRequest(input.toString(), null);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{}", "{", "[]", "\"text\"", "42"})
    void rejectsMissingOrMalformedRequestBodies(String input) throws Exception {
        assertBadRequest(input, null);
    }

    @Test
    void frontendCannotOverrideCalculatedPrices() throws Exception {
        ObjectNode input = (ObjectNode) mapper.readTree(INPUT);
        input.put("annual_total", 1);
        input.put("liability", 1);
        var response = mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(input.toString()))
                .andReturn().getResponse();
        // Reject unknown properties or ignore them; never accept their prices.
        if (response.getStatus() == 200) {
            assertDecimal("702.00", mapper.readTree(response.getContentAsString()).get("basic").get("annual_total"));
        } else {
            assertEquals(400, response.getStatus());
        }
    }

    @Test
    void endpointRequiresPost() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isMethodNotAllowed());
    }

    private void assertBadRequest(String input, String expectedField) throws Exception {
        String body = mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(input))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        JsonNode error = mapper.readTree(body);
        assertEquals(400, error.get("status").asInt());
        assertTrue(error.get("detail").isString());
        if (expectedField != null) {
            assertTrue(error.get("detail").asString().contains(expectedField));
        }
        assertFalse(body.contains("com.example"));
        assertFalse(body.contains("tools.jackson"));
        assertFalse(error.has("trace"));
    }

    private static void assertDecimal(String expected, JsonNode actual) {
        assertTrue(actual.isNumber());
        assertEquals(0, new BigDecimal(expected).compareTo(actual.decimalValue()));
    }
}
