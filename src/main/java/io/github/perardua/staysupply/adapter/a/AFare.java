package io.github.perardua.staysupply.adapter.a;

import java.time.temporal.ChronoUnit;
import java.util.List;

import io.github.perardua.staysupply.adapter.AvailabilityQuery;
import io.github.perardua.staysupply.adapter.a.SupplierAClient.ADailyRate;

/**
 * A의 기간 요금. 총액과 세액이 한 번의 판정으로 함께 정해지므로 한쪽만 null이 될 수 없다.
 */
record AFare(Long totalAmountIncludingTax, Long taxAmount) {

    private static final AFare UNKNOWN = new AFare(null, null);

    /**
     * A는 날짜별 세금 별도 단가를 주므로 기간 전체 총액으로 합산한다.
     * 요청 기간 밖의 날짜가 섞여 와도 더하지 않는다.
     *
     * <p>중복 날짜는 호출 전에 remainingRoomsByDate가 이미 막으므로
     * 기간 안에서 센 수가 곧 서로 다른 날짜의 수다.
     */
    static AFare of(List<ADailyRate> dailyRates, AvailabilityQuery query) {
        long totalAmountIncludingTax = 0L;
        long taxAmount = 0L;
        long pricedNights = 0L;
        for (ADailyRate rate : dailyRates) {
            if (rate.date() == null
                    || rate.date().isBefore(query.checkIn())
                    || !rate.date().isBefore(query.checkOut())) {
                continue;
            }
            totalAmountIncludingTax += rate.nightlyRate() + rate.taxAmount();
            taxAmount += rate.taxAmount();
            pricedNights++;
        }

        if (pricedNights != ChronoUnit.DAYS.between(query.checkIn(), query.checkOut())) {
            return UNKNOWN;
        }
        return new AFare(totalAmountIncludingTax, taxAmount);
    }
}
