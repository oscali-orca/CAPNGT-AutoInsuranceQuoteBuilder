package com.example.javabackend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import tools.jackson.core.JacksonException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiValidationExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                errors.putIfAbsent(camelCaseField(error.getField()), fieldMessage(
                        error.getField(), error.getCode(), error.getDefaultMessage())));

        return ResponseEntity.badRequest().body(
                new ApiError("VALIDATION_ERROR", "Invalid risk details", errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleMalformedRequest(HttpMessageNotReadableException exception) {
        String field = invalidField(exception);
        if (field != null) {
            String responseField = camelCaseField(field);
            return ResponseEntity.badRequest().body(new ApiError(
                    "VALIDATION_ERROR",
                    "Invalid details",
                    Map.of(responseField, fieldLabel(field) + " must be a whole number")));
        }
        return ResponseEntity.badRequest().body(new ApiError(
                "INVALID_REQUEST_BODY",
                "Request body contains malformed JSON or an invalid field value",
                Map.of()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleInvalidArgument(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError(
                "INVALID_REQUEST", exception.getMessage(), Map.of()));
    }

    private String fieldMessage(String field, String code, String defaultMessage) {
        String label = fieldLabel(field);

        if ("NotNull".equals(code) || "NotBlank".equals(code)) {
            return label + " is required";
        }
        if ("IntegerRange".equals(code)) {
            return label + " " + defaultMessage;
        }
        if ("AllowedIntegerValues".equals(code)) {
            return label + " must be 500, 1000, or 2000";
        }
        if ("Size".equals(code) && "quoteName".equals(field)) {
            return "Quote name must be 80 characters or fewer";
        }
        return label + " " + defaultMessage;
    }

    private String fieldLabel(String field) {
        return switch (field) {
            case "driverAge" -> "Driver age";
            case "driver_age" -> "Driver age";
            case "vehicleAge" -> "Vehicle age";
            case "vehicle_age" -> "Vehicle age";
            case "annualMileage" -> "Annual mileage";
            case "annual_mileage" -> "Annual mileage";
            case "priorClaims" -> "Prior claims";
            case "prior_claims" -> "Prior claims";
            case "comparisonDeductible" -> "Comparison deductible";
            case "comparison_deductible" -> "Comparison deductible";
            case "selectedPackage" -> "Selected package";
            case "quoteName" -> "Quote name";
            case "requestKey" -> "Request key";
            default -> field;
        };
    }

    private String camelCaseField(String field) {
        return switch (field) {
            case "driverAge", "driver_age" -> "driverAge";
            case "vehicleAge", "vehicle_age" -> "vehicleAge";
            case "annualMileage", "annual_mileage" -> "annualMileage";
            case "priorClaims", "prior_claims" -> "priorClaims";
            case "comparisonDeductible", "comparison_deductible" -> "comparisonDeductible";
            case "selectedPackage", "selected_package" -> "selectedPackage";
            case "quoteName", "quote_name" -> "quoteName";
            case "requestKey", "request_key" -> "requestKey";
            default -> field;
        };
    }

    private String invalidField(HttpMessageNotReadableException exception) {
        Throwable cause = exception;
        while (cause != null && !(cause instanceof JacksonException)) {
            cause = cause.getCause();
        }
        if (cause instanceof JacksonException jacksonException) {
            return jacksonException.getPath().stream()
                    .map(JacksonException.Reference::getPropertyName)
                    .filter(name -> name != null && !name.isBlank())
                    .reduce((first, second) -> second)
                    .orElse(null);
        }
        return null;
    }

    public record ApiError(String code, String message, Map<String, String> fieldErrors) {
    }
}
