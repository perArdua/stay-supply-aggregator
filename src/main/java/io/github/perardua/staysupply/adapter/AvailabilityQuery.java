package io.github.perardua.staysupply.adapter;

import java.time.LocalDate;

public record AvailabilityQuery(
        LocalDate checkIn,
        LocalDate checkOut,
        int adults,
        int children
) {
}
