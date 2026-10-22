package com.smartparking.model;

import java.util.Objects;

/** Mutable spot state; only valid lifecycle transitions are permitted. */
public abstract class ParkingSpot {
    private final String code;
    private final int floorId;
    private SpotStatus status;

    protected ParkingSpot(String code, int floorId, SpotStatus status) {
        if (code == null || !code.matches("SPOT-[0-9]{2,}")) {
            throw new IllegalArgumentException("Spot code must look like SPOT-01");
        }
        if (floorId <= 0) {
            throw new IllegalArgumentException("Floor ID must be positive");
        }
        this.code = code;
        this.floorId = floorId;
        this.status = Objects.requireNonNull(status, "Spot status is required");
    }

    public final String getCode() {
        return code;
    }

    public final int getFloorId() {
        return floorId;
    }

    public final synchronized SpotStatus getStatus() {
        return status;
    }

    public abstract SpotType getType();

    public abstract boolean isCompatible(Vehicle vehicle);

    public final synchronized boolean isAvailableFor(Vehicle vehicle) {
        return status == SpotStatus.AVAILABLE && isCompatible(vehicle);
    }

    public final synchronized void occupy(Vehicle vehicle) {
        if (status != SpotStatus.AVAILABLE) {
            throw new IllegalStateException(code + " is not available");
        }
        if (!isCompatible(vehicle)) {
            throw new IllegalArgumentException(code + " is incompatible with this vehicle");
        }
        status = SpotStatus.OCCUPIED;
    }

    public final synchronized void release() {
        if (status != SpotStatus.OCCUPIED) {
            throw new IllegalStateException(code + " is not occupied");
        }
        status = SpotStatus.AVAILABLE;
    }

    public final synchronized void markOutOfService() {
        if (status != SpotStatus.AVAILABLE) {
            throw new IllegalStateException("Only an available spot can be taken out of service");
        }
        status = SpotStatus.OUT_OF_SERVICE;
    }

    public final synchronized void returnToService() {
        if (status != SpotStatus.OUT_OF_SERVICE) {
            throw new IllegalStateException("Spot is not out of service");
        }
        status = SpotStatus.AVAILABLE;
    }
}
