package com.smartparking.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** A floor owns its spots. Spots must be added through ParkingGarage. */
public final class ParkingFloor {
    private final int id;
    private final int number;
    private final String name;
    private final Map<String, ParkingSpot> spots = new LinkedHashMap<>();

    public ParkingFloor(int id, int number, String name) {
        if (id <= 0 || number < 0) {
            throw new IllegalArgumentException("Floor ID must be positive and floor number nonnegative");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Floor name is required");
        }
        this.id = id;
        this.number = number;
        this.name = name.trim();
    }

    public int getId() {
        return id;
    }

    public int getNumber() {
        return number;
    }

    public String getName() {
        return name;
    }

    synchronized void addSpot(ParkingSpot spot) {
        Objects.requireNonNull(spot, "Spot is required");
        if (spot.getFloorId() != id || spots.containsKey(spot.getCode())) {
            throw new IllegalArgumentException("Spot floor mismatch or duplicate spot code");
        }
        spots.put(spot.getCode(), spot);
    }

    public synchronized Optional<ParkingSpot> findSpot(String code) {
        return Optional.ofNullable(spots.get(code));
    }

    public synchronized List<ParkingSpot> getSpots() {
        return List.copyOf(spots.values());
    }

    public synchronized int getCapacity() {
        return spots.size();
    }
}
