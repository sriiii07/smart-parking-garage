package com.smartparking.pricing;

import com.smartparking.model.Car;
import com.smartparking.model.ParkingSession;
import com.smartparking.model.PricingTier;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class OccupancyTierPricingStrategyTest {
    private final DynamicRateEngine engine = new DynamicRateEngine(
            OccupancyTierPricingStrategy.forDefaultGarage());

    @ParameterizedTest
    @CsvSource({"0,20", "1,20", "4,20", "5,40", "7,40", "8,60", "9,60", "10,60"})
    void defaultBoundaries(int occupiedBeforeEntry, int expectedFee) {
        assertEquals(new BigDecimal(expectedFee).setScale(2), engine.quote(occupiedBeforeEntry, 10));
    }

    @Test
    void fullGarageHasTierButServiceMustRejectAdmission() {
        assertEquals(new BigDecimal("60.00"), engine.quote(10, 10));
        // Quote calculation never grants permission to park; Phase 3 service checks availability.
    }

    @Test
    void rejectsNegativeAndAboveCapacityCountsAndMismatchedCapacity() {
        assertThrows(IllegalArgumentException.class, () -> engine.quote(-1, 10));
        assertThrows(IllegalArgumentException.class, () -> engine.quote(11, 10));
        assertThrows(IllegalArgumentException.class, () -> engine.quote(4, 100));
        assertThrows(IllegalArgumentException.class, () -> engine.quote(0, 0));
    }

    @Test
    void quotesUsingPreEntryCountAndDoesNotChangeExistingFee() {
        BigDecimal agreed = engine.quote(4, 10);
        ParkingSession session = new ParkingSession("T-5", new Car("KL07AB1234"),
                "SPOT-05", LocalDateTime.now(), agreed);
        assertEquals(new BigDecimal("20.00"), session.getLockedFee());
        assertEquals(new BigDecimal("40.00"), engine.quote(5, 10));
        assertEquals(new BigDecimal("20.00"), session.getLockedFee());
        assertEquals(new BigDecimal("40.00"), engine.quote(7, 10));
        assertEquals(new BigDecimal("60.00"), engine.quote(8, 10));
        assertEquals(new BigDecimal("40.00"), engine.quote(7, 10));
    }

    @Test
    void acceptsValidatedCustomIntegerThresholds() {
        DynamicRateEngine custom = new DynamicRateEngine(new OccupancyTierPricingStrategy(100, List.of(
                tier("Low", 0, 49, "20"), tier("Medium", 50, 79, "40"),
                tier("High", 80, 100, "60"))));
        assertEquals(new BigDecimal("20.00"), custom.quote(49, 100));
        assertEquals(new BigDecimal("40.00"), custom.quote(50, 100));
        assertEquals(new BigDecimal("60.00"), custom.quote(80, 100));
        assertEquals(new BigDecimal("60.00"), custom.quote(100, 100));
    }

    @Test
    void rejectsIncompleteOverlappingOrOutOfOrderConfigurations() {
        assertThrows(IllegalArgumentException.class, () -> new OccupancyTierPricingStrategy(0,
                List.of(tier("Only", 0, 0, "20"))));
        assertThrows(IllegalArgumentException.class, () -> new OccupancyTierPricingStrategy(10, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new OccupancyTierPricingStrategy(10,
                List.of(tier("Bad start", 1, 10, "20"))));
        assertThrows(IllegalArgumentException.class, () -> new OccupancyTierPricingStrategy(10,
                List.of(tier("Gap1", 0, 4, "20"), tier("Gap2", 6, 10, "40"))));
        assertThrows(IllegalArgumentException.class, () -> new OccupancyTierPricingStrategy(10,
                List.of(tier("Overlap1", 0, 5, "20"), tier("Overlap2", 5, 10, "40"))));
        assertThrows(IllegalArgumentException.class, () -> new OccupancyTierPricingStrategy(10,
                List.of(tier("Too long", 0, 11, "20"))));
        assertThrows(IllegalArgumentException.class, () -> new OccupancyTierPricingStrategy(10,
                List.of(tier("Short", 0, 9, "20"))));
        assertThrows(IllegalArgumentException.class, () -> new OccupancyTierPricingStrategy(10,
                List.of(tier("Second", 5, 10, "40"), tier("First", 0, 4, "20"))));
    }

    @Test
    void supportsSwappableStrategyWithoutEditingEngine() {
        DynamicRateEngine alternative = new DynamicRateEngine(
                (occupied, capacity) -> tier("Flat", 0, capacity, "25"));
        assertEquals(new BigDecimal("25.00"), alternative.quote(5, 10));
    }

    private static PricingTier tier(String name, int min, int max, String fee) {
        return new PricingTier(name, min, max, new BigDecimal(fee));
    }
}
