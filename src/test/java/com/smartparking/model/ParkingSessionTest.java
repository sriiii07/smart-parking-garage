package com.smartparking.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ParkingSessionTest {
    @Test
    void retainsOriginalFeeThroughExitAndRejectsRepeatExit() {
        LocalDateTime entry = LocalDateTime.of(2026, 10, 22, 10, 0);
        ParkingSession session = new ParkingSession("T-123", new Car("KL07AB1234"),
                "SPOT-01", entry, new BigDecimal("40.00"));
        session.complete(entry.plusHours(5));
        assertEquals(new BigDecimal("40.00"), session.getLockedFee());
        assertEquals(SessionStatus.COMPLETED, session.getStatus());
        assertEquals(PaymentStatus.PAID, session.getPaymentStatus());
        assertThrows(IllegalStateException.class, () -> session.complete(entry.plusHours(6)));
    }

    @Test
    void validatesRehydratedSessionState() {
        LocalDateTime entry = LocalDateTime.of(2026, 10, 22, 10, 0);
        Car car = new Car("KL07AB1234");
        assertThrows(IllegalArgumentException.class, () -> ParkingSession.restore("T-1", car,
                "SPOT-01", entry, new BigDecimal("20"), entry.plusHours(1),
                SessionStatus.ACTIVE, PaymentStatus.UNPAID));
        assertThrows(IllegalArgumentException.class, () -> ParkingSession.restore("T-1", car,
                "SPOT-01", entry, new BigDecimal("20"), null,
                SessionStatus.COMPLETED, PaymentStatus.PAID));
    }
}
