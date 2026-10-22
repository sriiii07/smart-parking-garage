package com.smartparking.service;

import com.smartparking.model.ParkingGarage;
import com.smartparking.model.ParkingSession;
import com.smartparking.model.ParkingSpot;
import com.smartparking.model.SpotStatus;
import com.smartparking.model.Vehicle;
import com.smartparking.model.VehicleType;
import com.smartparking.pricing.DynamicRateEngine;
import com.smartparking.validation.ValidationService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Phase 3 in-memory coordinator. Phase 4 adds JDBC commit-before-memory-update. */
public final class ParkingService {
    private final ParkingGarage garage;
    private final ParkingAllocationService allocation = new ParkingAllocationService();
    private final ValidationService validation = new ValidationService();
    private final TicketService tickets = new TicketService();
    private final SessionService sessions;
    private final GarageStatusService status;

    public ParkingService(ParkingGarage garage, DynamicRateEngine rateEngine) {
        this(garage, rateEngine, List.of());
    }

    /** Also used after persistence loads spots and sessions during application restart. */
    public ParkingService(ParkingGarage garage, DynamicRateEngine rateEngine,
                          Collection<ParkingSession> restoredSessions) {
        this.garage = Objects.requireNonNull(garage, "Garage is required");
        this.sessions = new SessionService(restoredSessions);
        this.status = new GarageStatusService(garage, rateEngine);
        status.snapshot(); // Validates capacity and pricing configuration immediately.
        Set<String> occupiedBySessions = new HashSet<>();
        for (ParkingSession session : sessions.activeSessions()) {
            if (!occupiedBySessions.add(session.getSpotCode()) ||
                    allocation.requireOccupied(garage, session.getSpotCode()).getStatus() != SpotStatus.OCCUPIED) {
                throw new IllegalStateException("Restored active sessions do not match occupied spots");
            }
        }
        if (occupiedBySessions.size() != garage.count(SpotStatus.OCCUPIED)) {
            throw new IllegalStateException("Restored occupied spots and active sessions disagree");
        }
    }

    public GarageSnapshot overview() {
        synchronized (garage) {
            return status.snapshot();
        }
    }

    public List<ParkingSpot> availableSpots(String registration, VehicleType type) {
        Vehicle vehicle = validation.vehicle(registration, type);
        synchronized (garage) {
            return allocation.availableCompatible(garage, vehicle);
        }
    }

    public ParkingSession enter(String registration, VehicleType type, String spotCode, BigDecimal confirmedFee) {
        Vehicle vehicle = validation.vehicle(registration, type);
        validation.confirmedFee(confirmedFee);
        synchronized (garage) {
            sessions.requireNoActiveVehicle(vehicle.getRegistrationNumber());
            ParkingSpot spot = allocation.requireAvailableCompatible(garage, spotCode, vehicle);
            GarageSnapshot before = status.snapshot();
            if (before.getAvailable() == 0) {
                throw new IllegalStateException("The garage has no available parking spaces");
            }
            if (before.getCurrentFee().compareTo(confirmedFee) != 0) {
                throw new QuoteChangedException(before.getCurrentFee());
            }
            String ticketCode = tickets.newTicketCode();
            // Very unlikely UUID collision; never overwrite an existing ticket.
            while (sessions.containsTicket(ticketCode)) {
                ticketCode = tickets.newTicketCode();
            }
            ParkingSession session = new ParkingSession(ticketCode, vehicle, spot.getCode(),
                    LocalDateTime.now(), before.getCurrentFee());
            spot.occupy(vehicle);
            try {
                sessions.add(session);
            } catch (RuntimeException failure) {
                spot.release(); // No partial admission on a failed in-memory registration.
                throw failure;
            }
            return session;
        }
    }

    public ParkingSession findActive(String ticketOrRegistration) {
        String lookup = validation.lookup(ticketOrRegistration);
        synchronized (garage) {
            return sessions.requireActive(lookup);
        }
    }

    public ParkingSession exit(String ticketOrRegistration) {
        String lookup = validation.lookup(ticketOrRegistration);
        synchronized (garage) {
            ParkingSession session = sessions.requireActive(lookup);
            ParkingSpot spot = allocation.requireOccupied(garage, session.getSpotCode());
            LocalDateTime exitTime = LocalDateTime.now();
            if (exitTime.isBefore(session.getEntryTime())) {
                throw new IllegalStateException("Exit cannot occur before entry");
            }
            // Preconditions were checked under the garage lock before either state is changed.
            sessions.complete(session, exitTime);
            spot.release();
            return session;
        }
    }

    public List<ParkingSession> activeSessions() {
        synchronized (garage) {
            return sessions.activeSessions();
        }
    }

    public List<ParkingSession> history() {
        synchronized (garage) {
            return sessions.completedSessions();
        }
    }
}
