package io.github.perardua.staysupply.adapter;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.perardua.staysupply.adapter.a.SupplierAClient;
import io.github.perardua.staysupply.adapter.a.SupplierAProperties;
import io.github.perardua.staysupply.adapter.b.SupplierBClient;
import io.github.perardua.staysupply.adapter.b.SupplierBProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * A는 HTTP 상태로, B는 본문 resultCode로 실패를 알린다.
 * 표현이 달라도 같은 사실은 같은 예외 타입으로 모여야 서킷의 실패 집계가 뜻을 갖는다.
 */
class SupplierFailureClassificationTest {

    private static final LocalDate CHECK_IN = LocalDate.of(2026, 9, 1);

    private static AvailabilityQuery oneNight() {
        return new AvailabilityQuery(CHECK_IN, CHECK_IN.plusDays(1), 2, 0);
    }

    private static List<SupplierOffer> searchA(int statusCode, String body) {
        SupplierAClient client = new SupplierAClient(
                StubExchange.respondingWith(statusCode, body),
                new SupplierAProperties("http://stub", "key", 1000, 10000, 10000, 10));
        return client.fetchAvailability(List.of("A-1"), oneNight()).block();
    }

    private static List<SupplierOffer> searchB(int statusCode, String body) {
        SupplierBClient client = new SupplierBClient(
                StubExchange.respondingWith(statusCode, body),
                new SupplierBProperties("http://stub", "key", 1000, 10000, 10000, 10));
        return client.fetchAvailability(List.of("B-1"), oneNight()).block();
    }

    @Test
    void A가_400_INVALID_DATE_RANGE로_답하면_요청_거부로_분류된다() {
        assertThatThrownBy(() -> searchA(400, "{\"error\":\"INVALID_DATE_RANGE\"}"))
                .isInstanceOf(SupplierClientErrorException.class);
    }

    @Test
    void A가_400_TOO_MANY_HOTEL_CODES로_답하면_요청_거부로_분류된다() {
        assertThatThrownBy(() -> searchA(400, "{\"error\":\"TOO_MANY_HOTEL_CODES\"}"))
                .isInstanceOf(SupplierClientErrorException.class);
    }

    @Test
    void A가_401로_답하면_요청_거부로_분류된다() {
        assertThatThrownBy(() -> searchA(401, "{\"error\":\"UNAUTHORIZED\"}"))
                .isInstanceOf(SupplierClientErrorException.class);
    }

    @Test
    void A가_429로_답하면_요청_거부로_분류된다() {
        // 4xx 전체가 요청 거부다. 호출 한도 초과를 타임아웃이나 공급사 오류로 세지 않는다.
        assertThatThrownBy(() -> searchA(429, "{\"error\":\"TOO_MANY_REQUESTS\"}"))
                .isInstanceOf(SupplierClientErrorException.class);
    }

    @Test
    void A가_500으로_답하면_공급사_오류로_분류된다() {
        assertThatThrownBy(() -> searchA(500, "{}"))
                .isInstanceOf(SupplierServerException.class);
    }

    @Test
    void A가_503으로_답하면_공급사_오류로_분류된다() {
        assertThatThrownBy(() -> searchA(503, "{}"))
                .isInstanceOf(SupplierServerException.class);
    }

    @Test
    void A가_200을_주고도_본문이_JSON이_아니면_해석_불가로_분류된다() {
        assertThatThrownBy(() -> searchA(200, "<html>gateway error</html>"))
                .isInstanceOf(SupplierMalformedException.class);
    }

    @Test
    void A의_503과_B의_E503은_같은_분류로_모인다() {
        // 실패 판정 통일의 핵심. 한쪽만 바뀌면 같은 사실이 다른 이름으로 집계된다.
        Throwable fromA = catchThrowable(() -> searchA(503, "{}"));
        Throwable fromB = catchThrowable(() -> searchB(200,
                "{\"resultCode\":\"E503\",\"resultMessage\":\"unavailable\",\"data\":null}"));

        assertThat(fromA).hasSameClassAs(fromB);
    }
}
