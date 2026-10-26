package com.example.javabackend.dto;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PricingRequest(
        @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer driverAge,
        @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer vehicleAge,
        @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer annualMileage,
        @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer priorClaims,
        @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer comparisonDeductible
) {
    // Reject fractional numbers and strings instead of allowing Jackson to coerce them.
    public static final class StrictIntegerDeserializer extends ValueDeserializer<Integer> {
        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context) {
            if (!parser.isExpectedNumberIntToken()) {
                return context.reportInputMismatch(Integer.class, "Value must be a whole-number JSON number");
            }
            return parser.getIntValue();
        }
    }
}
