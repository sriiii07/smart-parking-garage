package com.smartparking.service;

import java.util.Locale;
import java.util.UUID;

/** Random human-readable ticket codes; uniqueness is also checked by the session store. */
public final class TicketService {
    public String newTicketCode() {
        return "PK-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
    }
}
