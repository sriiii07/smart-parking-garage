package com.smartparking.pricing;

import com.smartparking.model.PricingTier;

/** Determines a new arrival's tier from occupancy BEFORE admission. */
@FunctionalInterface
public interface PricingStrategy {
    PricingTier tierFor(int occupiedBeforeEntry, int capacity);
}
