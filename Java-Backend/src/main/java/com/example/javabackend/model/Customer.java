package com.example.javabackend.model;

import com.example.javabackend.enums.PackageType;

import java.util.UUID;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@lombok.Data
public class Customer {
    private int driver_age;
    private int vehicle_age;
    private int annual_mileage;
    private int prior_claims;
    @Enumerated(EnumType.STRING)
    private PackageType selected_package;
    private int comparison_deductible;
    private String quote_name;
}
