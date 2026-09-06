package io.github.perardua.staysupply.adapter;

public record SupplierOffer(
        String supplierPropertyCode, // hotelCode / propertyId
        String propertyName, // hotelName / propertyName
        String supplierRoomTypeCode, // roomTypeCode / roomId
        String roomTypeName, // roomTypeName / roomName
        int maxOccupancy,
        int availableRooms,
        boolean breakfastIncluded,
        String currency,
        Long totalAmountIncludingTax, // A는 날짜별 nightlyRate + taxAmount, B는 totalPrice
        Long taxAmount
) {
}
