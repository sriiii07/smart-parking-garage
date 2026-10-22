package com.smartparking.service;

import com.smartparking.model.ParkingSession;
import com.smartparking.model.SessionStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** In-memory session indexes; the transactional repository will be added in Phase 4. */
public final class SessionService {
    private final Map<String, ParkingSession> byTicket = new LinkedHashMap<>();
    private final Map<String, String> activeRegistrations = new LinkedHashMap<>();
    private final Map<String, String> activeSpots = new LinkedHashMap<>();

    public SessionService(Collection<ParkingSession> restoredSessions) {
        Objects.requireNonNull(restoredSessions, "Restored sessions are required");
        for (ParkingSession session : restoredSessions) {
            add(session);
        }
    }

    public synchronized void requireNoActiveVehicle(String registration) {
        if (activeRegistrations.containsKey(registration)) {
            throw new IllegalStateException("Vehicle already has an active parking session");
        }
    }

    public synchronized boolean containsTicket(String ticketCode) {
        return byTicket.containsKey(ticketCode);
    }

    public synchronized void add(ParkingSession session) {
        Objects.requireNonNull(session, "Session is required");
        if (byTicket.containsKey(session.getTicketCode())) {
            throw new IllegalStateException("Ticket code already exists: " + session.getTicketCode());
        }
        if (session.getStatus() == SessionStatus.ACTIVE) {
            requireNoActiveVehicle(session.getVehicle().getRegistrationNumber());
            if (activeSpots.containsKey(session.getSpotCode())) {
                throw new IllegalStateException("Spot already belongs to an active session");
            }
        }
        byTicket.put(session.getTicketCode(), session);
        if (session.getStatus() == SessionStatus.ACTIVE) {
            activeRegistrations.put(session.getVehicle().getRegistrationNumber(), session.getTicketCode());
            activeSpots.put(session.getSpotCode(), session.getTicketCode());
        }
    }

    public synchronized ParkingSession requireActive(String ticketOrRegistration) {
        String key = ticketOrRegistration.trim().toUpperCase(Locale.ROOT);
        ParkingSession found = byTicket.get(key);
        if (found == null) {
            String registration = key.replaceAll("[\\s-]", "");
            found = byTicket.get(activeRegistrations.get(registration));
        }
        if (found == null) {
            throw new IllegalArgumentException("No active session matches the ticket or registration");
        }
        if (found.getStatus() != SessionStatus.ACTIVE) {
            throw new IllegalStateException("Ticket is already completed");
        }
        return found;
    }

    public synchronized void complete(ParkingSession session, LocalDateTime exitTime) {
        Objects.requireNonNull(session, "Session is required");
        if (byTicket.get(session.getTicketCode()) != session || session.getStatus() != SessionStatus.ACTIVE) {
            throw new IllegalStateException("Session is not an active session in this garage");
        }
        session.complete(exitTime);
        activeRegistrations.remove(session.getVehicle().getRegistrationNumber());
        activeSpots.remove(session.getSpotCode());
    }

    public synchronized List<ParkingSession> activeSessions() {
        List<ParkingSession> result = new ArrayList<>();
        byTicket.values().stream().filter(s -> s.getStatus() == SessionStatus.ACTIVE).forEach(result::add);
        return List.copyOf(result);
    }

    public synchronized List<ParkingSession> completedSessions() {
        List<ParkingSession> result = new ArrayList<>();
        byTicket.values().stream().filter(s -> s.getStatus() == SessionStatus.COMPLETED).forEach(result::add);
        return List.copyOf(result);
    }
}
