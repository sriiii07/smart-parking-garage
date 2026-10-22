package com.smartparking.web;

import com.smartparking.model.CompactSpot;
import com.smartparking.model.LargeSpot;
import com.smartparking.model.ParkingFloor;
import com.smartparking.model.ParkingGarage;
import com.smartparking.pricing.DynamicRateEngine;
import com.smartparking.pricing.OccupancyTierPricingStrategy;
import com.smartparking.service.ParkingService;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/** Tiny Java HTTP host for the browser UI. No Node, Spring, or client-side business rules. */
public final class WebApplication {
    private WebApplication() { }

    public static HttpServer createServer(int port) throws IOException {
        ParkingGarage garage = new ParkingGarage("Northstar Garage");
        garage.addFloor(new ParkingFloor(1, 0, "Ground floor"));
        for (int number = 1; number <= 10; number++) {
            String code = String.format("SPOT-%02d", number);
            garage.addSpot(number <= 8 ? new LargeSpot(code, 1) : new CompactSpot(code, 1));
        }
        ParkingService service = new ParkingService(garage, new DynamicRateEngine(
                OccupancyTierPricingStrategy.forDefaultGarage()));
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/", new ParkingHttpHandler(service, garage));
        server.setExecutor(Executors.newFixedThreadPool(8));
        return server;
    }

    public static void main(String[] args) throws IOException {
        String rawPort = System.getenv().getOrDefault("PORT", "8080");
        int port;
        try {
            port = Integer.parseInt(rawPort);
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("PORT must be between 1 and 65535", exception);
        }
        HttpServer server = createServer(port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
        server.start();
        System.out.println("Northstar Smart Parking running on http://0.0.0.0:" + port);
        System.out.println("Demo mode: records are held in memory and reset on restart.");
    }
}
