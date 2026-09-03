package io.github.perardua.staysupply.adapter;

import java.util.List;

public record PropertyListing(
        String supplierCode, // hotelCode / propertyId
        String name, // hotelName / propertyName
        List<RoomTypeListing> roomTypeListingList // roomTypes[] / rooms[]
) {
}