package com.example.javabackend.model;

import com.example.javabackend.enums.PackageType;

import java.math.BigInteger;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.hibernate.type.descriptor.jdbc.VarcharUUIDJdbcType;

@lombok.Data
public class saved_quote {
    private VarcharUUIDJdbcType quote_reference;
    private BigInteger customer_id;
    private BigInteger rate_configuration_id;
    private int driver_age;
    private int vehicle_age;
    private int annual_mileage;
    private int prior_claims;
    @Enumerated(EnumType.STRING)
    private PackageType selected_package;
    private int comparison_deductible;
    private String quote_name;
}
