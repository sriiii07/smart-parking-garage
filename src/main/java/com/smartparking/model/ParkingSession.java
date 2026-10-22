package com.smartparking.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

/** A confirmed parking session with a fixed one-time fee. */
public final class ParkingSession {
    private final String ticketCode;
    private final Vehicle vehicle;
    private final String spotCode;
    private final LocalDateTime entryTime;
    private final BigDecimal lockedFee;
    private LocalDateTime exitTime;
    private SessionStatus status;
    private PaymentStatus paymentStatus;

    public ParkingSession(String ticketCode, Vehicle vehicle, String spotCode,
                          LocalDateTime entryTime, BigDecimal lockedFee) {
        this(ticketCode, vehicle, spotCode, entryTime, lockedFee,
                null, SessionStatus.ACTIVE, PaymentStatus.UNPAID);
    }

    private ParkingSession(String ticketCode, Vehicle vehicle, String spotCode,
                           LocalDateTime entryTime, BigDecimal lockedFee, LocalDateTime exitTime,
                           SessionStatus status, PaymentStatus paymentStatus) {
        if (ticketCode == null || ticketCode.isBlank() || spotCode == null || spotCode.isBlank()) {
            throw new IllegalArgumentException("Ticket code and spot code are required");
        }
        this.ticketCode = ticketCode.trim();
        this.vehicle = Objects.requireNonNull(vehicle, "Vehicle is required");
        this.spotCode = spotCode.trim();
        this.entryTime = Objects.requireNonNull(entryTime, "Entry time is required");
        Objects.requireNonNull(lockedFee, "Agreed fee is required");
        if (lockedFee.signum() < 0) {
            throw new IllegalArgumentException("Agreed fee cannot be negative");
        }
        this.lockedFee = lockedFee.setScale(2, RoundingMode.UNNECESSARY);
        this.status = Objects.requireNonNull(status, "Session status is required");
        this.paymentStatus = Objects.requireNonNull(paymentStatus, "Payment status is required");
        if (status == SessionStatus.ACTIVE && (exitTime != null || paymentStatus != PaymentStatus.UNPAID)) {
            throw new IllegalArgumentException("Active sessions cannot have an exit time or be paid");
        }
        if (status == SessionStatus.COMPLETED && (exitTime == null || paymentStatus != PaymentStatus.PAID)) {
            throw new IllegalArgumentException("Completed sessions require an exit time and simulated payment");
        }
        if (exitTime != null && exitTime.isBefore(entryTime)) {
            throw new IllegalArgumentException("Exit cannot precede entry");
        }
        this.exitTime = exitTime;
    }

    /** Rebuild a session from persisted data without modifying its agreed fee. */
    public static ParkingSession restore(String ticketCode, Vehicle vehicle, String spotCode,
                                         LocalDateTime entryTime, BigDecimal lockedFee,
                                         LocalDateTime exitTime, SessionStatus status,
                                         PaymentStatus paymentStatus) {
        return new ParkingSession(ticketCode, vehicle, spotCode, entryTime, lockedFee,
                exitTime, status, paymentStatus);
    }

    public String getTicketCode() {
        return ticketCode;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public String getSpotCode() {
        return spotCode;
    }

    public LocalDateTime getEntryTime() {
        return entryTime;
    }

    public BigDecimal getLockedFee() {
        return lockedFee;
    }

    public synchronized LocalDateTime getExitTime() {
        return exitTime;
    }

    public synchronized SessionStatus getStatus() {
        return status;
    }

    public synchronized PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    /** Payment is simulated as part of a confirmed exit; no external payment processing. */
    public synchronized void complete(LocalDateTime time) {
        if (status != SessionStatus.ACTIVE) {
            throw new IllegalStateException("Ticket is already completed");
        }
        Objects.requireNonNull(time, "Exit time is required");
        if (time.isBefore(entryTime)) {
            throw new IllegalArgumentException("Exit cannot precede entry");
        }
        exitTime = time;
        paymentStatus = PaymentStatus.PAID;
        status = SessionStatus.COMPLETED;
    }
}
