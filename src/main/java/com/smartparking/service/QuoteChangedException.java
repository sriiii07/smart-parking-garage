package com.smartparking.service;

import java.math.BigDecimal;
import java.util.Objects;

/** A new fee must be presented to and reconfirmed by the driver. */
public final class QuoteChangedException extends IllegalStateException {
    private final BigDecimal updatedFee;

    public QuoteChangedException(BigDecimal updatedFee) {
        super("Parking price changed to INR " + updatedFee + "; review and confirm again");
        this.updatedFee = Objects.requireNonNull(updatedFee, "Updated fee is required");
    }

    public BigDecimal getUpdatedFee() {
        return updatedFee;
    }
}
