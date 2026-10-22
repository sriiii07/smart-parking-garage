package com.smartparking.pricing;

import com.smartparking.model.PricingTier;
import java.math.BigDecimal;
import java.util.Objects;

/** Read-only pricing engine; admission/availability checks belong to ParkingService. */
public final class DynamicRateEngine {
    private final PricingStrategy strategy;

    public DynamicRateEngine(PricingStrategy strategy) {
        this.strategy = Objects.requireNonNull(strategy, "Pricing strategy is required");
    }

    public PricingTier tierFor(int occupiedBeforeEntry, int capacity) {
        if (capacity <= 0 || occupiedBeforeEntry < 0 || occupiedBeforeEntry > capacity) {
            throw new IllegalArgumentException("Invalid garage capacity or occupied count");
        }
        return Objects.requireNonNull(strategy.tierFor(occupiedBeforeEntry, capacity),
                "Pricing strategy returned no tier");
    }

    public BigDecimal quote(int occupiedBeforeEntry, int capacity) {
        return tierFor(occupiedBeforeEntry, capacity).getFee();
    }
}
