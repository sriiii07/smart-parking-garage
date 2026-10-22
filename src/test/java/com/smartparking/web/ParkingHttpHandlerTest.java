package com.smartparking.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ParkingHttpHandlerTest {
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void browserAssetsAndRealJavaWorkflowAreServedOverHttp() throws Exception {
        HttpServer server = WebApplication.createServer(0);
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        try {
            HttpResponse<String> page = get(base, "/");
            assertEquals(200, page.statusCode());
            assertTrue(page.body().contains("Choose your space"));
            assertEquals(200, get(base, "/style.css").statusCode());
            assertEquals(200, get(base, "/app.js").statusCode());
            assertEquals(404, get(base, "/missing.js").statusCode());

            JsonNode empty = read(get(base, "/api/state"));
            assertEquals(10, empty.path("overview").path("available").asInt());
            assertEquals(20, empty.path("overview").path("fee").asInt());
            assertEquals("IN_MEMORY_DEMO", empty.path("mode").asText());
            assertEquals(10, empty.path("spots").size());

            String firstTicket = null;
            for (int n = 1; n <= 5; n++) {
                HttpResponse<String> response = post(base, "/api/entry", Map.of(
                        "registration", String.format("KL07AB%04d", n), "vehicleType", "CAR",
                        "spotCode", String.format("SPOT-%02d", n), "confirmedFee", "20.00"));
                assertEquals(201, response.statusCode());
                JsonNode session = read(response).path("session");
                assertEquals(20, session.path("lockedFee").asInt());
                if (n == 1) firstTicket = session.path("ticketCode").asText();
            }
            assertEquals(40, read(get(base, "/api/state")).path("overview").path("fee").asInt());
            HttpResponse<String> stale = post(base, "/api/entry", Map.of("registration", "KL07AB0006",
                    "vehicleType", "CAR", "spotCode", "SPOT-06", "confirmedFee", "20.00"));
            assertEquals(409, stale.statusCode());
            assertEquals("QUOTE_CHANGED", read(stale).path("error").asText());
            assertEquals(40, read(stale).path("updatedFee").asInt());
            assertEquals(5, read(get(base, "/api/state")).path("overview").path("occupied").asInt());
            assertEquals(201, post(base, "/api/entry", Map.of("registration", "KL07AB0006",
                    "vehicleType", "CAR", "spotCode", "SPOT-06", "confirmedFee", "40.00")).statusCode());
            assertEquals(firstTicket, read(post(base, "/api/lookup", Map.of("lookup", "KL07AB0001")))
                    .path("session").path("ticketCode").asText());
            HttpResponse<String> exited = post(base, "/api/exit", Map.of("lookup", firstTicket));
            assertEquals(200, exited.statusCode());
            assertEquals(20, read(exited).path("session").path("lockedFee").asInt());
            assertEquals("PAID", read(exited).path("session").path("paymentStatus").asText());
            assertEquals(1, read(get(base, "/api/state")).path("history").size());
            assertEquals(40, read(get(base, "/api/state")).path("overview").path("fee").asInt());
            assertEquals(409, post(base, "/api/exit", Map.of("lookup", firstTicket)).statusCode());
        } finally { server.stop(0); }
    }

    @Test
    void validationAndSpotCompatibilityAreEnforcedByJavaBackend() throws Exception {
        HttpServer server = WebApplication.createServer(0);
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        try {
            assertEquals(400, post(base, "/api/entry", Map.of("registration", "?", "vehicleType", "CAR",
                    "spotCode", "SPOT-01", "confirmedFee", "20.00")).statusCode());
            assertEquals(400, post(base, "/api/entry", Map.of("registration", "KL07AB1234", "vehicleType", "CAR",
                    "spotCode", "SPOT-09", "confirmedFee", "20.00")).statusCode());
            assertEquals(0, read(get(base, "/api/state")).path("overview").path("occupied").asInt());
        } finally { server.stop(0); }
    }

    private HttpResponse<String> get(String base, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> post(String base, String path, Map<String, String> data) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(data))).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private JsonNode read(HttpResponse<String> response) throws Exception {
        return mapper.readTree(response.body());
    }
}
