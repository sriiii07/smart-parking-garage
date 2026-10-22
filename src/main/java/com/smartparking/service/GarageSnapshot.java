package com.smartparking.service;

import java.math.BigDecimal;
import java.util.Objects;

/** Immutable overview for a single consistent service operation. */
public final class GarageSnapshot {
    private final int capacity;
    private final int occupied;
    private final int available;
    private final int reserved;
    private final int outOfService;
    private final String tierName;
    private final BigDecimal currentFee;

    public GarageSnapshot(int capacity, int occupied, int available, int reserved,
                          int outOfService, String tierName, BigDecimal currentFee) {
        if (capacity <= 0 || occupied < 0 || available < 0 || reserved < 0 || outOfService < 0
                || occupied + available + reserved + outOfService != capacity) {
            throw new IllegalArgumentException("Inconsistent garage counts");
        }
        this.capacity = capacity;
        this.occupied = occupied;
        this.available = available;
        this.reserved = reserved;
        this.outOfService = outOfService;
        this.tierName = Objects.requireNonNull(tierName, "Tier is required");
        this.currentFee = Objects.requireNonNull(currentFee, "Fee is required");
    }

    public int getCapacity() { return capacity; }
    public int getOccupied() { return occupied; }
    public int getAvailable() { return available; }
    public int getReserved() { return reserved; }
    public int getOutOfService() { return outOfService; }
    public String getTierName() { return tierName; }
    public BigDecimal getCurrentFee() { return currentFee; }
}
