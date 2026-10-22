package com.smartparking.validation;

import com.smartparking.model.Car;
import com.smartparking.model.Motorcycle;
import com.smartparking.model.Vehicle;
import com.smartparking.model.VehicleType;
import java.math.BigDecimal;
import java.util.Objects;

/** Converts user input to validated domain objects; the UI cannot bypass these checks. */
public final class ValidationService {
    public Vehicle vehicle(String registration, VehicleType type) {
        Objects.requireNonNull(type, "Vehicle type is required");
        switch (type) {
            case CAR:
                return new Car(registration);
            case MOTORCYCLE:
                return new Motorcycle(registration);
            default:
                throw new IllegalArgumentException("Unsupported vehicle type: " + type);
        }
    }

    public BigDecimal confirmedFee(BigDecimal fee) {
        if (fee == null || fee.signum() < 0 || fee.scale() > 2) {
            throw new IllegalArgumentException("Confirm a valid fee with at most two decimal places");
        }
        return fee;
    }

    public String lookup(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Enter a ticket code or vehicle registration");
        }
        return input.trim();
    }
}
