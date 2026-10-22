package com.smartparking.pricing;

import com.smartparking.model.PricingTier;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Integer occupancy ranges; all valid occupancy values, including 'full', have a tier. */
public final class OccupancyTierPricingStrategy implements PricingStrategy {
    private final int configuredCapacity;
    private final List<PricingTier> tiers;

    public OccupancyTierPricingStrategy(int capacity, List<PricingTier> tiers) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Garage capacity must be positive");
        }
        this.configuredCapacity = capacity;
        this.tiers = List.copyOf(Objects.requireNonNull(tiers, "Pricing tiers are required"));
        if (this.tiers.isEmpty()) {
            throw new IllegalArgumentException("At least one pricing tier is required");
        }
        long nextMinimum = 0;
        for (PricingTier tier : this.tiers) {
            if (tier.getMinimumOccupied() != nextMinimum || tier.getMaximumOccupied() > capacity) {
                throw new IllegalArgumentException("Pricing tiers must be ordered, non-overlapping, and contiguous within capacity");
            }
            nextMinimum = (long) tier.getMaximumOccupied() + 1;
        }
        if (nextMinimum != (long) capacity + 1) {
            throw new IllegalArgumentException("Pricing tiers must cover occupancy 0 through capacity");
        }
    }

    public static OccupancyTierPricingStrategy forDefaultGarage() {
        return new OccupancyTierPricingStrategy(10, List.of(
                new PricingTier("Tier 1 - Low occupancy", 0, 4, new BigDecimal("20.00")),
                new PricingTier("Tier 2 - Medium occupancy", 5, 7, new BigDecimal("40.00")),
                new PricingTier("Tier 3 - High occupancy", 8, 10, new BigDecimal("60.00"))));
    }

    public int getConfiguredCapacity() {
        return configuredCapacity;
    }

    public List<PricingTier> getTiers() {
        return tiers;
    }

    @Override
    public PricingTier tierFor(int occupiedBeforeEntry, int capacity) {
        if (capacity != configuredCapacity) {
            throw new IllegalArgumentException("Pricing tiers are configured for " + configuredCapacity + " spots, not " + capacity);
        }
        if (occupiedBeforeEntry < 0 || occupiedBeforeEntry > capacity) {
            throw new IllegalArgumentException("Occupied count must be between 0 and " + capacity);
        }
        for (PricingTier tier : tiers) {
            if (tier.includes(occupiedBeforeEntry)) {
                return tier;
            }
        }
        // Constructor validation makes this unreachable for valid input.
        throw new IllegalStateException("No pricing tier covers the occupied count");
    }
}
