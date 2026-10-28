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
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void setUp() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new PricingController(new PricingService()))
                .setControllerAdvice(new ApiValidationExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new JacksonJsonHttpMessageConverter(mapper)).build();
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        validator.destroy();
    }

    @Test
    void postsSnakeCaseComparisonAndSerializesAllBreakdowns() throws Exception {
        String body = mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(INPUT))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode response = mapper.readTree(body);
        assertEquals("TRAINING-1", response.get("rateVersion").asString());
        assertEquals("USD", response.get("currency").asString());
        assertEquals("ANNUAL", response.get("period").asString());
        assertEquals(1000, response.get("comparisonDeductible").asInt());
        assertDecimal("0.85", response.get("deductibleFactor"));
        assertDecimal("1.00", response.get("riskFactors").get("driverFactor"));
        assertDecimal("1.00", response.get("riskFactors").get("mileageFactor"));
        assertDecimal("1.00", response.get("riskFactors").get("claimsFactor"));
        assertDecimal("1.17", response.get("riskFactors").get("vehicleFactor"));
        assertDecimal("702.00", response.get("basic").get("annualTotal"));
        assertDecimal("906.00", response.get("standard").get("annualTotal"));
        assertDecimal("1008.00", response.get("enhanced").get("annualTotal"));
        assertEquals("BASIC", response.get("basic").get("packageType").asString());
        assertTrue(response.get("basic").get("deductible").isNull());
        assertFalse(response.get("basic").get("deductibleApplicable").asBoolean());
        assertDecimal("204.00", response.get("standard").get("collision"));
        assertDecimal("102.00", response.get("enhanced").get("comprehensive"));
        assertTrue(response.get("enhanced").get("deductibleApplicable").asBoolean());
        assertFalse(response.has("rate_version"));
        assertFalse(response.get("riskFactors").has("driver_factor"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"driver_age", "vehicle_age", "annual_mileage", "prior_claims", "comparison_deductible"})
    void rejectsMissingAndNullRequiredFields(String field) throws Exception {
        ObjectNode input = (ObjectNode) mapper.readTree(INPUT);
        input.remove(field);
        assertFieldError(input.toString(), toJavaFieldName(field), requiredMessage(field));
        input.putNull(field);
        assertFieldError(input.toString(), toJavaFieldName(field), requiredMessage(field));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(value = {
            "driver_age|17|Driver age must be between 18 and 80",
            "vehicle_age|21|Vehicle age must be between 0 and 20",
            "annual_mileage|50001|Annual mileage must be between 0 and 50000",
            "prior_claims|4|Prior claims must be between 0 and 3",
            "comparison_deductible|750|Comparison deductible must be 500, 1000, or 2000"
    }, delimiter = '|')
    void returnsFieldSpecificMessagesForOutOfRangeAndDisallowedValues(
            String field, int value, String expectedMessage) throws Exception {
        ObjectNode input = (ObjectNode) mapper.readTree(INPUT);
        input.put(field, value);
        assertFieldError(input.toString(), toJavaFieldName(field), expectedMessage);
    }

    @Test
    void returnsMultipleErrorsUnderTheirCorrespondingFields() throws Exception {
        ObjectNode input = (ObjectNode) mapper.readTree(INPUT);
        input.put("driver_age", 17);
        input.put("annual_mileage", 50001);

        String body = mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(input.toString()))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        JsonNode error = mapper.readTree(body);

        assertErrorEnvelope(error, "VALIDATION_ERROR", "Invalid risk details");
        assertEquals(2, error.get("fieldErrors").size());
        assertEquals("Driver age must be between 18 and 80",
                error.get("fieldErrors").get("driverAge").asString());
        assertEquals("Annual mileage must be between 0 and 50000",
                error.get("fieldErrors").get("annualMileage").asString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "[]", "\"text\"", "42"})
    void rejectsMissingOrMalformedRequestBodies(String input) throws Exception {
        assertBadRequest(input);
    }

    @Test
    void emptyObjectReturnsAllRequiredFieldErrors() throws Exception {
        String body = mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        JsonNode error = mapper.readTree(body);

        assertErrorEnvelope(error, "VALIDATION_ERROR", "Invalid risk details");
        assertEquals(5, error.get("fieldErrors").size(), body);
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
            assertDecimal("702.00", mapper.readTree(response.getContentAsString()).get("basic").get("annualTotal"));
        } else {
            assertEquals(400, response.getStatus());
        }
    }

    @Test
    void endpointRequiresPost() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isMethodNotAllowed());
    }

    private void assertBadRequest(String input) throws Exception {
        String body = mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(input))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        JsonNode error = mapper.readTree(body);
        assertEquals(3, error.size(), body);
        assertTrue(error.get("code").isString(), body);
        assertTrue(error.get("message").isString(), body);
        assertTrue(error.get("fieldErrors").isObject(), body);
        assertFalse(body.contains("com.example"));
        assertFalse(body.contains("tools.jackson"));
        assertFalse(error.has("trace"));
    }

    private void assertFieldError(String input, String field, String expectedMessage) throws Exception {
        String body = mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(input))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        JsonNode error = mapper.readTree(body);

        assertErrorEnvelope(error, "VALIDATION_ERROR", "Invalid risk details");
        assertTrue(error.get("fieldErrors").has(field), body);
        assertEquals(expectedMessage, error.get("fieldErrors").get(field).asString());
        assertEquals(1, error.get("fieldErrors").size());
    }

    private void assertErrorEnvelope(JsonNode error, String code, String message) {
        assertEquals(3, error.size());
        assertEquals(code, error.get("code").asString());
        assertEquals(message, error.get("message").asString());
        assertTrue(error.get("fieldErrors").isObject());
        assertNull(error.get("status"));
        assertNull(error.get("detail"));
    }

    private String requiredMessage(String snakeCaseField) {
        return switch (snakeCaseField) {
            case "driver_age" -> "Driver age is required";
            case "vehicle_age" -> "Vehicle age is required";
            case "annual_mileage" -> "Annual mileage is required";
            case "prior_claims" -> "Prior claims is required";
            case "comparison_deductible" -> "Comparison deductible is required";
            default -> throw new IllegalArgumentException(snakeCaseField);
        };
    }

    private String toJavaFieldName(String snakeCaseField) {
        return switch (snakeCaseField) {
            case "driver_age" -> "driverAge";
            case "vehicle_age" -> "vehicleAge";
            case "annual_mileage" -> "annualMileage";
            case "prior_claims" -> "priorClaims";
            case "comparison_deductible" -> "comparisonDeductible";
            default -> throw new IllegalArgumentException(snakeCaseField);
        };
    }

    private static void assertDecimal(String expected, JsonNode actual) {
        assertTrue(actual.isNumber());
        assertEquals(0, new BigDecimal(expected).compareTo(actual.decimalValue()));
    }
}
