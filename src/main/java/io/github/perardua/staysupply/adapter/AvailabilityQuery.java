package io.github.perardua.staysupply.adapter;

import java.time.LocalDate;

public record AvailabilityQuery(
        LocalDate checkIn,
        LocalDate checkOut,
        int adults,
        int children
) {
    public AvailabilityQuery {
        if (checkIn == null || checkOut == null) {
            throw new IllegalArgumentException(
                    "checkIn and checkOut must not be null: checkIn=" + checkIn + ", checkOut=" + checkOut);
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException(
                    "checkOut must be after checkIn: checkIn=" + checkIn + ", checkOut=" + checkOut);
        }
        if (adults < 1) {
            throw new IllegalArgumentException("adults must be at least 1: adults=" + adults);
        }
        if (children < 0) {
            throw new IllegalArgumentException("children must not be negative: children=" + children);
        }
    }
}
