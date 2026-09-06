package io.github.perardua.staysupply.adapter.b;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.perardua.staysupply.adapter.AvailabilityQuery;
import io.github.perardua.staysupply.adapter.StubExchange;
import io.github.perardua.staysupply.adapter.SupplierClientErrorException;
import io.github.perardua.staysupply.adapter.SupplierMalformedException;
import io.github.perardua.staysupply.adapter.SupplierOffer;
import io.github.perardua.staysupply.adapter.SupplierServerException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * B는 장애 상황에서도 HTTP 200을 준다. 실패 판정은 본문의 resultCode가 맡는다.
 * 이 검사가 사라지면 B가 죽어도 "상품이 없다"로 조용히 나간다.
 */
class SupplierBResultCodeTest {

    private static final LocalDate CHECK_IN = LocalDate.of(2026, 9, 1);

    private static final String SUCCESS_BODY = """
            {"resultCode":"0000","resultMessage":"OK","data":{"items":[
              {"propertyId":"B-1","propertyName":"Riverside","roomId":"R-1","roomName":"Twin",
               "maxOccupancy":2,"breakfastIncluded":true,"currency":"KRW","totalPrice":452000,
               "inventory":[{"date":"2026-09-01","remainingRooms":3}]}]}}
            """;

    private static String failureBody(String resultCode) {
        return "{\"resultCode\":\"" + resultCode + "\",\"resultMessage\":\"failed\",\"data\":null}";
    }

    private static SupplierBProperties properties() {
        return new SupplierBProperties("http://stub", "key", 1000, 10000, 10000, 10);
    }

    private static AvailabilityQuery oneNight() {
        return new AvailabilityQuery(CHECK_IN, CHECK_IN.plusDays(1), 2, 0);
    }

    private static List<SupplierOffer> search(String body) {
        SupplierBClient client = new SupplierBClient(StubExchange.respondingWith(200, body), properties());
        return client.fetchAvailability(List.of("B-1"), oneNight()).block();
    }

    @Test
    void resultCode가_0000이고_data가_있으면_상품_목록을_받는다() {
        assertThat(search(SUCCESS_BODY)).hasSize(1);
    }

    @Test
    void resultCode가_E503이면_HTTP_200이어도_공급사_오류로_분류된다() {
        assertThatThrownBy(() -> search(failureBody("E503")))
                .isInstanceOf(SupplierServerException.class);
    }

    @Test
    void resultCode가_E500이면_HTTP_200이어도_공급사_오류로_분류된다() {
        assertThatThrownBy(() -> search(failureBody("E500")))
                .isInstanceOf(SupplierServerException.class);
    }

    @Test
    void resultCode가_E400이면_요청_거부로_분류된다() {
        assertThatThrownBy(() -> search(failureBody("E400")))
                .isInstanceOf(SupplierClientErrorException.class);
    }

    @Test
    void resultCode가_E401이면_요청_거부로_분류된다() {
        assertThatThrownBy(() -> search(failureBody("E401")))
                .isInstanceOf(SupplierClientErrorException.class);
    }

    @Test
    void resultCode가_E429이면_요청_거부로_분류된다() {
        assertThatThrownBy(() -> search(failureBody("E429")))
                .isInstanceOf(SupplierClientErrorException.class);
    }

    @Test
    void 모르는_resultCode는_해석_불가로_분류된다() {
        assertThatThrownBy(() -> search(failureBody("E999")))
                .isInstanceOf(SupplierMalformedException.class);
    }

    @Test
    void resultCode가_0000인데_data가_null이면_해석_불가로_분류된다() {
        // 성공 응답이 데이터를 안 주는 것은 "재고가 없다"가 아니라 스펙 위반으로 본다.
        assertThatThrownBy(() -> search("{\"resultCode\":\"0000\",\"resultMessage\":\"OK\",\"data\":null}"))
                .isInstanceOf(SupplierMalformedException.class);
    }

    @Test
    void 본문이_JSON이_아니면_해석_불가로_분류된다() {
        assertThatThrownBy(() -> search("<html>gateway error</html>"))
                .isInstanceOf(SupplierMalformedException.class);
    }
}
