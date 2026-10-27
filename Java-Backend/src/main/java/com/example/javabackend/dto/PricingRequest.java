package com.example.javabackend.dto;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.example.javabackend.validation.IntegerRange;
import com.example.javabackend.validation.AllowedIntegerValues;
import jakarta.validation.constraints.NotNull;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PricingRequest(
        @NotNull @IntegerRange(min = 18, max = 80) @JsonAlias("driverAge")
        @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer driverAge,
        @NotNull @IntegerRange(min = 0, max = 20) @JsonAlias("vehicleAge")
        @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer vehicleAge,
        @NotNull @IntegerRange(min = 0, max = 50000) @JsonAlias("annualMileage")
        @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer annualMileage,
        @NotNull @IntegerRange(min = 0, max = 3) @JsonAlias("priorClaims")
        @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer priorClaims,
        @NotNull @AllowedIntegerValues({500, 1000, 2000}) @JsonAlias("comparisonDeductible")
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
