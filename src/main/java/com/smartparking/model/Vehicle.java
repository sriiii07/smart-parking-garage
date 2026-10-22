package com.smartparking.model;

import java.util.Locale;
import java.util.Objects;

/** A validated, normalized vehicle identity. */
public abstract class Vehicle {
    private final String registrationNumber;

    protected Vehicle(String registrationNumber) {
        Objects.requireNonNull(registrationNumber, "Registration is required");
        String normalized = registrationNumber.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        if (!normalized.matches("(?=.*[A-Z])(?=.*[0-9])[A-Z0-9]{5,15}")) {
            throw new IllegalArgumentException("Registration must have 5-15 letters/digits, including a letter and a digit");
        }
        this.registrationNumber = normalized;
    }

    public final String getRegistrationNumber() {
        return registrationNumber;
    }

    public abstract VehicleType getType();

    /** Required size units: compact = 1, large = 2. */
    public abstract int getRequiredSpaceUnits();
}
