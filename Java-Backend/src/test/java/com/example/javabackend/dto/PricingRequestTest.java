package com.example.javabackend.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PricingRequestTest {
    private static final String INPUT = """
            {"driver_age":30,"vehicle_age":3,"annual_mileage":12000,
             "prior_claims":0,"comparison_deductible":1000}
            """;
    private static final String[] FIELDS = {
            "driver_age", "vehicle_age", "annual_mileage", "prior_claims", "comparison_deductible"
    };
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void readsAndWritesSnakeCaseIntegerInputs() {
        PricingRequest request = mapper.readValue(INPUT, PricingRequest.class);
        assertEquals(new PricingRequest(30, 3, 12000, 0, 1000), request);
        assertEquals(mapper.readTree(INPUT), mapper.readTree(mapper.writeValueAsString(request)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"30.5", "30.0", "3e1", "\"30\"", "\"\"", "true", "[]", "{}",
            "2147483648", "-2147483649"})
    void rejectsMalformedIntegerValuesForEveryField(String invalidJson) {
        for (String field : FIELDS) {
            ObjectNode input = (ObjectNode) mapper.readTree(INPUT);
            input.set(field, mapper.readTree(invalidJson));
            assertThrows(JacksonException.class,
                    () -> mapper.readValue(input.toString(), PricingRequest.class), field);
        }
    }

    @Test
    void preservesMissingAndNullValuesForServiceValidation() {
        assertEquals(new PricingRequest(null, null, null, null, null),
                mapper.readValue("{}", PricingRequest.class));
        for (String field : FIELDS) {
            ObjectNode input = (ObjectNode) mapper.readTree(INPUT);
            input.putNull(field);
            PricingRequest request = mapper.readValue(input.toString(), PricingRequest.class);
            assertTrue(mapper.valueToTree(request).get(field).isNull());
        }
    }
}
