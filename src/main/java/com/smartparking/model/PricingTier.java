package com.smartparking.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Immutable configured range; validating the complete set of ranges belongs to the pricing strategy. */
public final class PricingTier {
    private final String name;
    private final int minimumOccupied;
    private final int maximumOccupied;
    private final BigDecimal fee;

    public PricingTier(String name, int minimumOccupied, int maximumOccupied, BigDecimal fee) {
        if (name == null || name.isBlank() || minimumOccupied < 0 || maximumOccupied < minimumOccupied) {
            throw new IllegalArgumentException("Invalid pricing tier name or occupancy range");
        }
        Objects.requireNonNull(fee, "Fee is required");
        if (fee.signum() < 0) {
            throw new IllegalArgumentException("Fee cannot be negative");
        }
        this.name = name.trim();
        this.minimumOccupied = minimumOccupied;
        this.maximumOccupied = maximumOccupied;
        this.fee = fee.setScale(2, RoundingMode.UNNECESSARY);
    }

    public String getName() {
        return name;
    }

    public int getMinimumOccupied() {
        return minimumOccupied;
    }

    public int getMaximumOccupied() {
        return maximumOccupied;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public boolean includes(int occupied) {
        return occupied >= minimumOccupied && occupied <= maximumOccupied;
    }
}
