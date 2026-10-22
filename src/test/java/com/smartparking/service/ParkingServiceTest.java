package com.smartparking.service;

import com.smartparking.model.Car;
import com.smartparking.model.CompactSpot;
import com.smartparking.model.LargeSpot;
import com.smartparking.model.ParkingFloor;
import com.smartparking.model.ParkingGarage;
import com.smartparking.model.ParkingSession;
import com.smartparking.model.ParkingSpot;
import com.smartparking.model.PricingTier;
import com.smartparking.model.SessionStatus;
import com.smartparking.model.SpotStatus;
import com.smartparking.model.VehicleType;
import com.smartparking.pricing.DynamicRateEngine;
import com.smartparking.pricing.OccupancyTierPricingStrategy;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ParkingServiceTest {
    private static final BigDecimal LOW = new BigDecimal("20.00");
    private static final BigDecimal MEDIUM = new BigDecimal("40.00");
    private static final BigDecimal HIGH = new BigDecimal("60.00");

    private ParkingGarage garage() {
        ParkingGarage garage = new ParkingGarage("Test Garage");
        garage.addFloor(new ParkingFloor(1, 0, "Ground"));
        for (int i = 1; i <= 10; i++) {
            String code = String.format("SPOT-%02d", i);
            garage.addSpot(i <= 8 ? new LargeSpot(code, 1) : new CompactSpot(code, 1));
        }
        return garage;
    }

    private ParkingService service(ParkingGarage garage) {
        return new ParkingService(garage, new DynamicRateEngine(
                OccupancyTierPricingStrategy.forDefaultGarage()));
    }

    private ParkingSession enterCar(ParkingService service, int index, BigDecimal fee) {
        return service.enter("KL07AB" + String.format("%04d", index), VehicleType.CAR,
                String.format("SPOT-%02d", index), fee);
    }

    @Test
    void emptyGarageAndThresholdSequenceUsePreEntryOccupancy() {
        ParkingService service = service(garage());
        assertEquals(10, service.overview().getCapacity());
        assertEquals(0, service.overview().getOccupied());
        assertEquals(10, service.overview().getAvailable());
        assertEquals(LOW, service.overview().getCurrentFee());
        ParkingSession first = enterCar(service, 1, LOW);
        assertEquals(LOW, first.getLockedFee());
        for (int i = 2; i <= 4; i++) enterCar(service, i, LOW);
        assertEquals(LOW, service.overview().getCurrentFee());
        ParkingSession fifth = enterCar(service, 5, LOW);
        assertEquals(LOW, fifth.getLockedFee());
        assertEquals(5, service.overview().getOccupied());
        assertEquals(MEDIUM, service.overview().getCurrentFee());
        ParkingSession sixth = enterCar(service, 6, MEDIUM);
        assertEquals(MEDIUM, sixth.getLockedFee());
        assertEquals(LOW, first.getLockedFee());
    }

    @Test
    void highTierFeeLocksAndExitLowersNextQuoteAtBothBoundaries() {
        ParkingService service = service(garage());
        List<ParkingSession> entered = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            entered.add(enterCar(service, i, i <= 5 ? LOW : MEDIUM));
        }
        assertEquals(HIGH, service.overview().getCurrentFee());
        ParkingSession ninth = service.enter("KL07AB0009", VehicleType.MOTORCYCLE, "SPOT-09", HIGH);
        assertEquals(HIGH, ninth.getLockedFee());
        assertEquals(HIGH, service.overview().getCurrentFee());
        service.exit(entered.get(0).getTicketCode()); // 9 -> 8, still high
        service.exit(entered.get(1).getTicketCode()); // 8 -> 7, now medium
        assertEquals(MEDIUM, service.overview().getCurrentFee());
        assertEquals(HIGH, ninth.getLockedFee());
        assertEquals(MEDIUM, entered.get(5).getLockedFee());
        service.exit(entered.get(2).getTicketCode());
        service.exit(entered.get(3).getTicketCode()); // 5 occupied
        assertEquals(MEDIUM, service.overview().getCurrentFee());
        service.exit(entered.get(4).getTicketCode()); // 5 -> 4
        assertEquals(LOW, service.overview().getCurrentFee());
        assertEquals(MEDIUM, entered.get(5).getLockedFee());
        assertEquals(HIGH, ninth.getLockedFee());
        assertEquals(4, service.overview().getOccupied());
        assertEquals(6, service.overview().getAvailable());
    }

    @Test
    void exitUsesStoredFeeReleasesSpotAndCannotBeRepeated() {
        ParkingService service = service(garage());
        ParkingSession first = enterCar(service, 1, LOW);
        assertSame(first, service.findActive("kl 07 ab 0001"));
        assertSame(first, service.findActive(first.getTicketCode()));
        ParkingSession exited = service.exit(first.getTicketCode());
        assertEquals(LOW, exited.getLockedFee());
        assertEquals(SessionStatus.COMPLETED, exited.getStatus());
        assertNotNull(exited.getExitTime());
        assertEquals(0, service.overview().getOccupied());
        assertEquals(1, service.history().size());
        assertThrows(IllegalStateException.class, () -> service.exit(first.getTicketCode()));
        assertEquals(10, service.overview().getAvailable());
        assertEquals(LOW, enterCar(service, 1, LOW).getLockedFee());
    }

    @Test
    void rejectsDoubleAllocationAndDuplicateActiveRegistrationWithoutPartialAdmission() {
        ParkingService service = service(garage());
        enterCar(service, 1, LOW);
        assertThrows(IllegalStateException.class, () -> service.enter("KL07AB0011",
                VehicleType.CAR, "SPOT-01", LOW));
        assertThrows(IllegalStateException.class, () -> service.enter("kl 07 ab 0001",
                VehicleType.CAR, "SPOT-02", LOW));
        assertEquals(1, service.overview().getOccupied());
        assertEquals(1, service.activeSessions().size());
    }

    @Test
    void rejectsIncompatibleUnavailableAndInvalidSelection() {
        ParkingGarage garage = garage();
        ParkingService service = service(garage);
        assertEquals(8, service.availableSpots("KL07AB0001", VehicleType.CAR).size());
        assertEquals(10, service.availableSpots("KL07AB0001", VehicleType.MOTORCYCLE).size());
        assertThrows(IllegalArgumentException.class, () -> service.enter("KL07AB0001",
                VehicleType.CAR, "SPOT-09", LOW));
        garage.findSpot("SPOT-02").orElseThrow().markOutOfService();
        assertThrows(IllegalStateException.class, () -> service.enter("KL07AB0001",
                VehicleType.CAR, "SPOT-02", LOW));
        assertThrows(IllegalArgumentException.class, () -> service.enter("KL07AB0001",
                VehicleType.CAR, "SPOT-99", LOW));
        assertThrows(IllegalArgumentException.class, () -> service.enter("",
                VehicleType.CAR, "SPOT-01", LOW));
        assertEquals(0, service.overview().getOccupied());
        assertEquals(1, service.overview().getOutOfService());
    }

    @Test
    void staleQuoteRequiresConfirmationAgainAndCannotCreatePartialSession() {
        ParkingService service = service(garage());
        for (int i = 1; i <= 5; i++) enterCar(service, i, LOW);
        QuoteChangedException changed = assertThrows(QuoteChangedException.class,
                () -> service.enter("KL07AB0006", VehicleType.CAR, "SPOT-06", LOW));
        assertEquals(MEDIUM, changed.getUpdatedFee());
        assertEquals(5, service.overview().getOccupied());
        assertEquals(5, service.activeSessions().size());
        ParkingSession accepted = enterCar(service, 6, changed.getUpdatedFee());
        assertEquals(MEDIUM, accepted.getLockedFee());
    }

    @Test
    void fullGarageStillHasTierButCannotAdmitAnotherVehicle() {
        ParkingService service = service(garage());
        for (int i = 1; i <= 8; i++) enterCar(service, i, i <= 5 ? LOW : MEDIUM);
        service.enter("KL07AB0009", VehicleType.MOTORCYCLE, "SPOT-09", HIGH);
        service.enter("KL07AB0010", VehicleType.MOTORCYCLE, "SPOT-10", HIGH);
        assertEquals(0, service.overview().getAvailable());
        assertEquals(HIGH, service.overview().getCurrentFee());
        assertThrows(IllegalStateException.class, () -> service.enter("KL07AB0011",
                VehicleType.CAR, "SPOT-01", HIGH));
        assertEquals(10, service.activeSessions().size());
    }

    @Test
    void restoresActiveSessionsFromLoadedRecordsAndRejectsInconsistentState() {
        ParkingService beforeRestart = service(garage());
        ParkingSession first = enterCar(beforeRestart, 1, LOW);
        ParkingGarage reloaded = garage();
        reloaded.findSpot("SPOT-01").orElseThrow().occupy(new Car("KL07AB0001"));
        ParkingSession fromDatabase = ParkingSession.restore(first.getTicketCode(),
                new Car(first.getVehicle().getRegistrationNumber()), first.getSpotCode(),
                first.getEntryTime(), first.getLockedFee(), null,
                first.getStatus(), first.getPaymentStatus());
        ParkingService afterRestart = new ParkingService(reloaded, new DynamicRateEngine(
                OccupancyTierPricingStrategy.forDefaultGarage()), List.of(fromDatabase));
        assertEquals(1, afterRestart.overview().getOccupied());
        assertEquals(LOW, afterRestart.findActive(first.getTicketCode()).getLockedFee());
        assertThrows(IllegalStateException.class, () -> afterRestart.enter("KL07AB0001",
                VehicleType.CAR, "SPOT-02", LOW));
        assertThrows(IllegalStateException.class, () -> service(reloaded));
    }

    @Test
    void simultaneousRequestsForSameSpotAdmitOnlyOne() throws Exception {
        ParkingService service = service(garage());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Boolean> first = () -> attemptSameSpot(service, "KL07AB0001", start);
            Callable<Boolean> second = () -> attemptSameSpot(service, "KL07AB0002", start);
            Future<Boolean> a = executor.submit(first);
            Future<Boolean> b = executor.submit(second);
            start.countDown();
            assertEquals(1, (a.get(5, TimeUnit.SECONDS) ? 1 : 0)
                    + (b.get(5, TimeUnit.SECONDS) ? 1 : 0));
            assertEquals(1, service.overview().getOccupied());
            assertEquals(1, service.activeSessions().size());
        } finally {
            executor.shutdownNow();
        }
    }

    private static boolean attemptSameSpot(ParkingService service, String registration, CountDownLatch start)
            throws InterruptedException {
        start.await();
        try {
            service.enter(registration, VehicleType.CAR, "SPOT-01", LOW);
            return true;
        } catch (IllegalStateException expected) {
            return false;
        }
    }

    @Test
    void reservedSpotCannotBeAllocatedOrCountedAsOccupied() {
        ParkingGarage garage = new ParkingGarage("Reserved Test");
        garage.addFloor(new ParkingFloor(1, 0, "Ground"));
        garage.addSpot(new LargeSpot("SPOT-01", 1, SpotStatus.RESERVED));
        DynamicRateEngine engine = new DynamicRateEngine(new OccupancyTierPricingStrategy(1,
                List.of(new PricingTier("Low", 0, 1, LOW))));
        ParkingService service = new ParkingService(garage, engine);
        assertEquals(0, service.overview().getOccupied());
        assertEquals(1, service.overview().getReserved());
        assertEquals(0, service.availableSpots("KL07AB0001", VehicleType.CAR).size());
        assertThrows(IllegalStateException.class, () -> service.enter("KL07AB0001",
                VehicleType.CAR, "SPOT-01", LOW));
        assertEquals(0, service.activeSessions().size());
    }
}
