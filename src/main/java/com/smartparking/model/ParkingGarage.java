package com.smartparking.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Garage configuration and derived spot totals; no duplicate occupancy counter. */
public final class ParkingGarage {
    private final String name;
    private final Map<Integer, ParkingFloor> floors = new LinkedHashMap<>();

    public ParkingGarage(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Garage name is required");
        }
        this.name = name.trim();
    }

    public String getName() {
        return name;
    }

    public synchronized void addFloor(ParkingFloor floor) {
        Objects.requireNonNull(floor, "Floor is required");
        if (floors.containsKey(floor.getId()) || floors.values().stream()
                .anyMatch(existing -> existing.getNumber() == floor.getNumber())) {
            throw new IllegalArgumentException("Floor ID or number already exists");
        }
        // A floor is registered before its spots. This prevents spot codes from bypassing garage validation.
        if (floor.getCapacity() != 0) {
            throw new IllegalArgumentException("Register an empty floor, then add spots through the garage");
        }
        floors.put(floor.getId(), floor);
    }

    public synchronized void addSpot(ParkingSpot spot) {
        Objects.requireNonNull(spot, "Spot is required");
        ParkingFloor floor = floors.get(spot.getFloorId());
        if (floor == null) {
            throw new IllegalArgumentException("No registered floor for " + spot.getCode());
        }
        if (findSpot(spot.getCode()).isPresent()) {
            throw new IllegalArgumentException("Spot code already exists: " + spot.getCode());
        }
        floor.addSpot(spot);
    }

    public synchronized Optional<ParkingFloor> findFloor(int floorId) {
        return Optional.ofNullable(floors.get(floorId));
    }

    public synchronized List<ParkingFloor> getFloors() {
        return List.copyOf(floors.values());
    }

    public synchronized Optional<ParkingSpot> findSpot(String code) {
        return floors.values().stream().map(floor -> floor.findSpot(code))
                .filter(Optional::isPresent).map(Optional::get).findFirst();
    }

    public synchronized List<ParkingSpot> getSpots() {
        List<ParkingSpot> all = new ArrayList<>();
        floors.values().forEach(floor -> all.addAll(floor.getSpots()));
        return List.copyOf(all);
    }

    public synchronized int getCapacity() {
        return getSpots().size();
    }

    public synchronized long count(SpotStatus status) {
        Objects.requireNonNull(status, "Spot status is required");
        return getSpots().stream().filter(spot -> spot.getStatus() == status).count();
    }
}
