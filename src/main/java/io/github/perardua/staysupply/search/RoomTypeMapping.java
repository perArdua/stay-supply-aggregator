package io.github.perardua.staysupply.search;

import io.github.perardua.staysupply.supplier.Supplier;

public record RoomTypeMapping(
        Supplier supplier,
        String supplierPropertyCode,
        long propertyId,
        String supplierRoomTypeCode,
        long roomTypeId
) {
}
