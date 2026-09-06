package io.github.perardua.staysupply.adapter.a;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.perardua.staysupply.adapter.AvailabilityQuery;
import io.github.perardua.staysupply.adapter.a.SupplierAClient.ADailyRate;

import static org.assertj.core.api.Assertions.assertThat;

class AFareTest {

    private static final LocalDate FIRST_NIGHT = LocalDate.of(2026, 9, 1);

    private static AvailabilityQuery nights(int nights) {
        return new AvailabilityQuery(FIRST_NIGHT, FIRST_NIGHT.plusDays(nights), 2, 0);
    }

    /** 첫날부터 dayOffset일 뒤의 날짜별 단가 */
    private static ADailyRate rate(int dayOffset, long nightlyRate, long taxAmount) {
        return new ADailyRate(FIRST_NIGHT.plusDays(dayOffset), 1, nightlyRate, taxAmount);
    }

    private static List<ADailyRate> threeNights() {
        return List.of(
                rate(0, 120000, 12000),
                rate(1, 150000, 15000),
                rate(2, 120000, 12000));
    }

    @Test
    void 총액은_날짜별_단가와_세금을_모두_더한_값이다() {
        assertThat(AFare.of(threeNights(), nights(3)).totalAmountIncludingTax()).isEqualTo(429000L);
    }

    @Test
    void 세액은_날짜별_세금만_더한_값이다() {
        assertThat(AFare.of(threeNights(), nights(3)).taxAmount()).isEqualTo(39000L);
    }

    @Test
    void 숙박이_하루면_총액은_그날의_단가와_세금을_더한_값이다() {
        assertThat(AFare.of(List.of(rate(0, 120000, 12000)), nights(1)).totalAmountIncludingTax())
                .isEqualTo(132000L);
    }

    @Test
    void 날짜마다_단가가_다르면_각_날짜의_값이_그대로_총액에_반영된다() {
        // 첫날 값에 박수를 곱하는 구현이면 330000이 나온다.
        List<ADailyRate> rates = List.of(
                rate(0, 100000, 10000),
                rate(1, 200000, 20000),
                rate(2, 300000, 30000));

        assertThat(AFare.of(rates, nights(3)).totalAmountIncludingTax()).isEqualTo(660000L);
    }

    @Test
    void 세금이_0인_날짜가_섞여_있어도_총액은_단가와_세금의_합이다() {
        List<ADailyRate> rates = List.of(
                rate(0, 100000, 0),
                rate(1, 100000, 10000),
                rate(2, 100000, 0));

        assertThat(AFare.of(rates, nights(3)).totalAmountIncludingTax()).isEqualTo(310000L);
    }

    @Test
    void 요청_기간_밖의_날짜는_총액에_더해지지_않는다() {
        // 2박을 요청했는데 사흘치가 왔다. 셋째 날의 132000은 빠져야 한다.
        assertThat(AFare.of(threeNights(), nights(2)).totalAmountIncludingTax()).isEqualTo(297000L);
    }

    @Test
    void 요청_기간의_날짜가_빠져_있으면_총액은_null이다() {
        // 4박을 요청했는데 사흘치만 왔다. 3박 금액을 4박 상품에 붙이면 틀린 값이다.
        assertThat(AFare.of(threeNights(), nights(4)).totalAmountIncludingTax()).isNull();
    }

    @Test
    void 요청_기간의_날짜가_빠져_있으면_세액도_null이다() {
        assertThat(AFare.of(threeNights(), nights(4)).taxAmount()).isNull();
    }

    @Test
    void 총액과_세액은_항상_함께_채워지거나_함께_null이_된다() {
        List<AFare> fares = List.of(
                AFare.of(threeNights(), nights(3)),
                AFare.of(threeNights(), nights(2)),
                AFare.of(threeNights(), nights(4)),
                AFare.of(List.of(), nights(3)),
                AFare.of(List.of(rate(9, 100000, 10000)), nights(1)));

        assertThat(fares).allSatisfy(fare ->
                assertThat(fare.totalAmountIncludingTax() == null).isEqualTo(fare.taxAmount() == null));
    }
}
