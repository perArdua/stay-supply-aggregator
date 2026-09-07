package io.github.perardua.staysupply.search;

import io.github.perardua.staysupply.supplier.Supplier;
import io.swagger.v3.oas.annotations.media.Schema;

public record StayOffer(
        Long propertyId,
        String propertyName,
        Long roomTypeId,
        String roomTypeName,
        int maxOccupancy,
        int availableRooms,
        boolean breakfastIncluded,
        String currency,
        // 요청 기간의 날짜가 빠진 응답은 총액을 믿을 수 없어 null로 내보낸다.
        @Schema(nullable = true, description = "기간 전체 고객 결제 금액. 공급사가 요청 기간의 요금을 다 주지 않으면 null")
        Long totalAmountIncludingTax,
        // 세액을 주지 않는 공급사가 있어 null이 들어온다. 0으로 채우면 세금이 없는 상품과 구분되지 않는다.
        @Schema(nullable = true, description = "총액에 포함된 세금. 세액을 주지 않는 공급사는 null이며 0과 뜻이 다르다")
        Long taxAmount,
        // 여러 공급사의 결과를 한 리스트에 병합하므로 객체 스스로 출처를 나타내야 한다.
        Supplier supplier
) {
}
