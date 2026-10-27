package com.example.javabackend.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Arrays;

public class AllowedIntegerValuesValidator implements ConstraintValidator<AllowedIntegerValues, Integer> {
    private int[] allowed;

    @Override
    public void initialize(AllowedIntegerValues constraint) {
        allowed = constraint.value();
    }

    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {
        return value == null || Arrays.stream(allowed).anyMatch(candidate -> candidate == value);
    }
}
