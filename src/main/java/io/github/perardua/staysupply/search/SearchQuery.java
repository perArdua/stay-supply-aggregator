package io.github.perardua.staysupply.search;

import java.time.LocalDate;

public record SearchQuery(
        LocalDate checkIn,
        LocalDate checkOut,
        int adults,
        int children
) {
}
