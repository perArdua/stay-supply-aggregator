package io.github.perardua.staysupply.search;

import io.github.perardua.staysupply.supplier.Supplier;

public record StayOffer(
        Long propertyId,
        String propertyName,
        Long roomTypeId,
        String roomTypeName,
        int maxOccupancy,
        int availableRooms,
        boolean breakfastIncluded,
        String currency,
        long totalAmountIncludingTax,
        // 세액을 주지 않는 공급사가 있어 null이 들어온다. 0으로 채우면 세금이 없는 상품과 구분되지 않는다.
        Long taxAmount,
        // 여러 공급사의 결과를 한 리스트에 병합하므로 객체 스스로 출처를 나타내야 한다.
        Supplier supplier
) {
}
