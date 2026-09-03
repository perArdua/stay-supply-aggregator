package io.github.perardua.staysupply.adapter;

public record RoomTypeListing(
        String supplierCode, // roomTypeCode / roomId
        String name, // roomTypeName / roomName
        int maxOccupancy
) {
}