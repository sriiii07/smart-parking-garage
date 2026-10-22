package com.smartparking.service;

import com.smartparking.model.ParkingGarage;
import com.smartparking.model.ParkingSpot;
import com.smartparking.model.SpotStatus;
import com.smartparking.model.Vehicle;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Validates allocation; spot status changes happen only during coordinated entry/exit. */
public final class ParkingAllocationService {
    public List<ParkingSpot> availableCompatible(ParkingGarage garage, Vehicle vehicle) {
        Objects.requireNonNull(garage, "Garage is required");
        Objects.requireNonNull(vehicle, "Vehicle is required");
        return garage.getSpots().stream().filter(spot -> spot.isAvailableFor(vehicle))
                .collect(Collectors.toUnmodifiableList());
    }

    public ParkingSpot requireAvailableCompatible(ParkingGarage garage, String spotCode, Vehicle vehicle) {
        Objects.requireNonNull(garage, "Garage is required");
        Objects.requireNonNull(vehicle, "Vehicle is required");
        if (spotCode == null || spotCode.isBlank()) {
            throw new IllegalArgumentException("Select a parking spot");
        }
        ParkingSpot spot = garage.findSpot(spotCode.trim()).orElseThrow(
                () -> new IllegalArgumentException("Unknown parking spot: " + spotCode));
        if (spot.getStatus() != SpotStatus.AVAILABLE) {
            throw new IllegalStateException(spot.getCode() + " is " + spot.getStatus());
        }
        if (!spot.isCompatible(vehicle)) {
            throw new IllegalArgumentException(spot.getCode() + " is incompatible with " + vehicle.getType());
        }
        return spot;
    }

    public ParkingSpot requireOccupied(ParkingGarage garage, String spotCode) {
        ParkingSpot spot = garage.findSpot(spotCode).orElseThrow(
                () -> new IllegalStateException("Assigned spot no longer exists: " + spotCode));
        if (spot.getStatus() != SpotStatus.OCCUPIED) {
            throw new IllegalStateException("Assigned spot is not occupied: " + spotCode);
        }
        return spot;
    }
}
