package com.smartparking.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.model.Car;
import com.smartparking.model.Motorcycle;
import com.smartparking.model.ParkingGarage;
import com.smartparking.model.ParkingSession;
import com.smartparking.model.ParkingSpot;
import com.smartparking.model.VehicleType;
import com.smartparking.service.GarageSnapshot;
import com.smartparking.service.ParkingService;
import com.smartparking.service.QuoteChangedException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Routes a small same-origin JSON API and bundled static files to Java services. */
public final class ParkingHttpHandler implements HttpHandler {
    private static final Logger LOG = Logger.getLogger(ParkingHttpHandler.class.getName());
    private static final int MAX_BODY = 8192;
    private final ParkingService service;
    private final ParkingGarage garage;
    private final ObjectMapper json = new ObjectMapper();

    public ParkingHttpHandler(ParkingService service, ParkingGarage garage) {
        this.service = Objects.requireNonNull(service);
        this.garage = Objects.requireNonNull(garage);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if (path.startsWith("/api/")) {
                handleApi(exchange, method, path);
            } else if ("GET".equals(method) || "HEAD".equals(method)) {
                staticFile(exchange, path);
            } else {
                error(exchange, 405, "METHOD_NOT_ALLOWED", "Use GET for pages");
            }
        } catch (QuoteChangedException exception) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("error", "QUOTE_CHANGED");
            response.put("message", exception.getMessage());
            response.put("updatedFee", exception.getUpdatedFee());
            writeJson(exchange, 409, response);
        } catch (IllegalArgumentException exception) {
            error(exchange, 400, "INVALID_REQUEST", exception.getMessage());
        } catch (IllegalStateException exception) {
            error(exchange, 409, "CONFLICT", exception.getMessage());
        } catch (Exception exception) {
            LOG.log(Level.SEVERE, "Unexpected web request failure", exception);
            error(exchange, 500, "SERVER_ERROR", "Something went wrong. Please retry the action.");
        } finally {
            exchange.close();
        }
    }

    private void handleApi(HttpExchange exchange, String method, String path) throws IOException {
        if ("GET".equals(method) && "/api/state".equals(path)) {
            writeJson(exchange, 200, state());
        } else if ("POST".equals(method) && "/api/entry".equals(path)) {
            JsonNode input = requestJson(exchange);
            String registration = required(input, "registration");
            VehicleType type;
            try {
                type = VehicleType.valueOf(required(input, "vehicleType").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Choose CAR or MOTORCYCLE");
            }
            BigDecimal confirmedFee;
            try {
                confirmedFee = new BigDecimal(required(input, "confirmedFee"));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Confirmed fee must be a valid number");
            }
            ParkingSession ticket = service.enter(registration, type, required(input, "spotCode"), confirmedFee);
            writeJson(exchange, 201, Map.of("session", session(ticket), "state", state()));
        } else if ("POST".equals(method) && "/api/lookup".equals(path)) {
            ParkingSession ticket = service.findActive(required(requestJson(exchange), "lookup"));
            writeJson(exchange, 200, Map.of("session", session(ticket)));
        } else if ("POST".equals(method) && "/api/exit".equals(path)) {
            ParkingSession ticket = service.exit(required(requestJson(exchange), "lookup"));
            writeJson(exchange, 200, Map.of("session", session(ticket), "state", state()));
        } else {
            error(exchange, 404, "NOT_FOUND", "Unknown API route or method");
        }
    }

    private JsonNode requestJson(HttpExchange exchange) throws IOException {
        String type = exchange.getRequestHeaders().getFirst("Content-Type");
        if (type == null || !type.toLowerCase(Locale.ROOT).startsWith("application/json")) {
            throw new IllegalArgumentException("Send application/json");
        }
        byte[] body = exchange.getRequestBody().readNBytes(MAX_BODY + 1);
        if (body.length > MAX_BODY) throw new IllegalArgumentException("Request is too large");
        try {
            JsonNode node = json.readTree(body);
            if (node == null || !node.isObject()) throw new IllegalArgumentException("Send a JSON object");
            return node;
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid JSON request");
        }
    }

    private static String required(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Provide " + field);
        }
        return value.asText().trim();
    }

    private Map<String, Object> state() {
        // Service mutations also lock the garage, so this response is a consistent snapshot.
        synchronized (garage) {
            GarageSnapshot summary = service.overview();
            Map<String, Object> overview = new LinkedHashMap<>();
            overview.put("capacity", summary.getCapacity());
            overview.put("occupied", summary.getOccupied());
            overview.put("available", summary.getAvailable());
            overview.put("reserved", summary.getReserved());
            overview.put("outOfService", summary.getOutOfService());
            overview.put("tier", summary.getTierName());
            overview.put("fee", summary.getCurrentFee());
            List<Map<String, Object>> spots = new ArrayList<>();
            Car car = new Car("DEMO1");
            Motorcycle motorcycle = new Motorcycle("DEMO2");
            for (ParkingSpot spot : garage.getSpots()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("code", spot.getCode());
                item.put("floor", spot.getFloorId());
                item.put("type", spot.getType().name());
                item.put("status", spot.getStatus().name());
                List<String> compatible = new ArrayList<>();
                if (spot.isCompatible(car)) compatible.add("CAR");
                if (spot.isCompatible(motorcycle)) compatible.add("MOTORCYCLE");
                item.put("compatibleTypes", compatible);
                spots.add(item);
            }
            return Map.of("garageName", garage.getName(), "overview", overview, "spots", spots,
                    "active", service.activeSessions().stream().map(this::session).toList(),
                    "history", service.history().stream().map(this::session).toList(),
                    "mode", "IN_MEMORY_DEMO");
        }
    }

    private Map<String, Object> session(ParkingSession item) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ticketCode", item.getTicketCode());
        data.put("registration", item.getVehicle().getRegistrationNumber());
        data.put("vehicleType", item.getVehicle().getType().name());
        data.put("spotCode", item.getSpotCode());
        data.put("entryTime", item.getEntryTime().toString());
        data.put("exitTime", item.getExitTime() == null ? null : item.getExitTime().toString());
        data.put("lockedFee", item.getLockedFee());
        data.put("status", item.getStatus().name());
        data.put("paymentStatus", item.getPaymentStatus().name());
        return data;
    }

    private void staticFile(HttpExchange exchange, String path) throws IOException {
        String file;
        String mime;
        switch (path) {
            case "/": case "/index.html": file = "index.html"; mime = "text/html; charset=UTF-8"; break;
            case "/style.css": file = "style.css"; mime = "text/css; charset=UTF-8"; break;
            case "/app.js": file = "app.js"; mime = "text/javascript; charset=UTF-8"; break;
            default: error(exchange, 404, "NOT_FOUND", "Page not found"); return;
        }
        byte[] bytes;
        try (InputStream resource = getClass().getResourceAsStream("/web/" + file)) {
            if (resource == null) throw new IOException("Missing bundled asset: " + file);
            bytes = resource.readAllBytes();
        }
        exchange.getResponseHeaders().set("Content-Type", mime);
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, "HEAD".equals(exchange.getRequestMethod()) ? -1 : bytes.length);
        if (!"HEAD".equals(exchange.getRequestMethod())) {
            try (OutputStream output = exchange.getResponseBody()) { output.write(bytes); }
        }
    }

    private void error(HttpExchange exchange, int status, String code, String message) throws IOException {
        writeJson(exchange, status, Map.of("error", code, "message", message == null ? "Request failed" : message));
    }

    private void writeJson(HttpExchange exchange, int status, Object data) throws IOException {
        byte[] bytes = json.writeValueAsBytes(data);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) { output.write(bytes); }
    }
}
