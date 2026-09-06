package io.github.perardua.staysupply.adapter.a;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.perardua.staysupply.adapter.AvailabilityQuery;
import io.github.perardua.staysupply.adapter.StubExchange;
import io.github.perardua.staysupply.adapter.SupplierMalformedException;
import io.github.perardua.staysupply.adapter.SupplierOffer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AFare는 "기간 안에서 센 날짜 수 = 서로 다른 날짜 수"를 전제로 누락을 판정한다.
 * 그 전제를 세우는 것이 중복 날짜 검출이므로, 검출이 사라지면 요금이 조용히 틀린다.
 */
class SupplierAResponseTest {

    private static final LocalDate CHECK_IN = LocalDate.of(2026, 9, 1);

    private static List<SupplierOffer> search(String body) {
        SupplierAClient client = new SupplierAClient(
                StubExchange.respondingWith(200, body),
                new SupplierAProperties("http://stub", "key", 1000, 10000, 10000, 10));
        return client.fetchAvailability(List.of("A-1"), new AvailabilityQuery(
                CHECK_IN, CHECK_IN.plusDays(2), 2, 0)).block();
    }

    @Test
    void 같은_날짜가_두_번_오면_해석_불가로_분류된다() {
        // 2박 요청에 09-01이 두 번 온다. 날짜 수만 세면 2박을 다 받은 것처럼 보이지만
        // 실제로는 09-02가 없고 09-01 요금이 두 번 더해진다.
        String duplicateDates = """
                {"items":[{"hotelCode":"A-1","hotelName":"Riverside","roomTypeCode":"R-1",
                  "roomTypeName":"Twin","maxOccupancy":2,"breakfastIncluded":false,"currency":"KRW",
                  "dailyRates":[
                    {"date":"2026-09-01","remainingRooms":3,"nightlyRate":120000,"taxAmount":12000},
                    {"date":"2026-09-01","remainingRooms":1,"nightlyRate":150000,"taxAmount":15000}]}]}
                """;

        assertThatThrownBy(() -> search(duplicateDates))
                .isInstanceOf(SupplierMalformedException.class);
    }
}
