package com.example.javabackend.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class IntegerRangeValidator implements ConstraintValidator<IntegerRange, Integer> {
    private int min;
    private int max;

    @Override
    public void initialize(IntegerRange constraint) {
        min = constraint.min();
        max = constraint.max();
    }

    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {
        return value == null || (value >= min && value <= max);
    }
}
