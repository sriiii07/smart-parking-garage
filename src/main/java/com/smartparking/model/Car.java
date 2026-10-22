package com.smartparking.model;

public final class Car extends Vehicle {
    public Car(String registrationNumber) {
        super(registrationNumber);
    }

    @Override
    public VehicleType getType() {
        return VehicleType.CAR;
    }

    @Override
    public int getRequiredSpaceUnits() {
        return 2;
    }
}
