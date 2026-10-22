package com.smartparking.model;

import java.util.Objects;

/** A large bay accepts either a car or a motorcycle. */
public final class LargeSpot extends ParkingSpot {
    public LargeSpot(String code, int floorId) {
        this(code, floorId, SpotStatus.AVAILABLE);
    }

    public LargeSpot(String code, int floorId, SpotStatus status) {
        super(code, floorId, status);
    }

    @Override
    public SpotType getType() {
        return SpotType.LARGE;
    }

    @Override
    public boolean isCompatible(Vehicle vehicle) {
        return Objects.requireNonNull(vehicle, "Vehicle is required").getRequiredSpaceUnits() <= 2;
    }
}
