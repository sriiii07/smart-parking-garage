package com.smartparking.model;

public final class Motorcycle extends Vehicle {
    public Motorcycle(String registrationNumber) {
        super(registrationNumber);
    }

    @Override
    public VehicleType getType() {
        return VehicleType.MOTORCYCLE;
    }

    @Override
    public int getRequiredSpaceUnits() {
        return 1;
    }
}
