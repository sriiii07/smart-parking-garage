package com.smartparking.model;

import java.util.Objects;

/** A compact bay is suitable only for a one-unit vehicle (motorcycle). */
public final class CompactSpot extends ParkingSpot {
    public CompactSpot(String code, int floorId) {
        this(code, floorId, SpotStatus.AVAILABLE);
    }

    public CompactSpot(String code, int floorId, SpotStatus status) {
        super(code, floorId, status);
    }

    @Override
    public SpotType getType() {
        return SpotType.COMPACT;
    }

    @Override
    public boolean isCompatible(Vehicle vehicle) {
        return Objects.requireNonNull(vehicle, "Vehicle is required").getRequiredSpaceUnits() <= 1;
    }
}
