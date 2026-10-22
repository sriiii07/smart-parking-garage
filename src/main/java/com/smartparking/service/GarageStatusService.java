package com.smartparking.service;

import com.smartparking.model.ParkingGarage;
import com.smartparking.model.PricingTier;
import com.smartparking.model.SpotStatus;
import com.smartparking.pricing.DynamicRateEngine;
import java.util.Objects;

/** Derives counts and the next arrival's quote from authoritative spot state. */
public final class GarageStatusService {
    private final ParkingGarage garage;
    private final DynamicRateEngine rateEngine;

    public GarageStatusService(ParkingGarage garage, DynamicRateEngine rateEngine) {
        this.garage = Objects.requireNonNull(garage, "Garage is required");
        this.rateEngine = Objects.requireNonNull(rateEngine, "Pricing engine is required");
    }

    public GarageSnapshot snapshot() {
        int capacity = garage.getCapacity();
        int occupied = Math.toIntExact(garage.count(SpotStatus.OCCUPIED));
        int available = Math.toIntExact(garage.count(SpotStatus.AVAILABLE));
        int reserved = Math.toIntExact(garage.count(SpotStatus.RESERVED));
        int outOfService = Math.toIntExact(garage.count(SpotStatus.OUT_OF_SERVICE));
        PricingTier tier = rateEngine.tierFor(occupied, capacity);
        return new GarageSnapshot(capacity, occupied, available, reserved, outOfService,
                tier.getName(), tier.getFee());
    }
}
