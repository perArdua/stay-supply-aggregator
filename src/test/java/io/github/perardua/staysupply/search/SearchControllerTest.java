package io.github.perardua.staysupply.search;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import io.github.perardua.staysupply.adapter.AvailabilityQuery;
import io.github.perardua.staysupply.supplier.Supplier;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * 요청이 응답이 되기까지의 경계만 본다. 서비스는 대역이라 공급사도 DB도 필요 없다.
 * 필드명이나 상태 코드가 바뀌면 로직은 전부 통과하고 클라이언트만 깨진다.
 */
// DB를 쓰지 않는 테스트라 도커를 제외한다.
// application.yaml이 테스트에서도 도커를 켜 두므로, 이 줄을 지우면 도커 없는 곳에서 깨진다.
@WebMvcTest(controllers = SearchController.class,
        properties = "spring.docker.compose.skip.in-tests=true")
class SearchControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    SearchService searchService;

    private static final String SEARCH = "/api/v1/stays/search";

    private void respondWith(SearchResponse response) {
        given(searchService.search(any(AvailabilityQuery.class))).willReturn(Mono.just(response));
    }

    private static StayOffer offer(Long total, Long tax) {
        return new StayOffer(101L, "Riverside Hotel Seoul", 2001L, "Deluxe Twin",
                2, 1, false, "KRW", total, tax, Supplier.A);
    }

    private static SearchResponse okResponse(StayOffer... offers) {
        return new SearchResponse(List.of(offers), List.of(SupplierStatus.ok(Supplier.A)));
    }

    private org.assertj.core.api.AbstractStringAssert<?> get(String query) {
        return assertThat(mvc.get().uri(SEARCH + query)).hasStatusOk().bodyText();
    }

    @Test
    void 정상_결과는_200으로_나간다() {
        respondWith(okResponse(offer(429000L, 39000L)));

        assertThat(mvc.get().uri(SEARCH + "?checkIn=2026-09-01&checkOut=2026-09-04&adults=2&children=0"))
                .hasStatusOk();
    }

    @Test
    void 상품_필드명이_계약대로_나간다() {
        // 이름이 바뀌면 서버는 멀쩡하고 클라이언트만 깨진다.
        respondWith(okResponse(offer(429000L, 39000L)));

        get("?checkIn=2026-09-01&checkOut=2026-09-04&adults=2&children=0")
                .contains("\"propertyId\":101", "\"propertyName\":\"Riverside Hotel Seoul\"",
                        "\"roomTypeId\":2001", "\"roomTypeName\":\"Deluxe Twin\"",
                        "\"maxOccupancy\":2", "\"availableRooms\":1",
                        "\"breakfastIncluded\":false", "\"currency\":\"KRW\"",
                        "\"totalAmountIncludingTax\":429000", "\"taxAmount\":39000",
                        "\"supplier\":\"A\"");
    }

    @Test
    void 응답은_offers와_suppliers_두_묶음으로_나간다() {
        respondWith(okResponse(offer(429000L, 39000L)));

        get("?checkIn=2026-09-01&checkOut=2026-09-04&adults=2&children=0")
                .contains("\"offers\":", "\"suppliers\":");
    }

    @Test
    void 총액을_신뢰할_수_없는_상품은_필드가_생략되지_않고_null로_나간다() {
        // 필드가 통째로 빠지면 클라이언트가 "모른다"와 "안 왔다"를 구분할 수 없다.
        respondWith(okResponse(offer(null, null)));

        get("?checkIn=2026-09-01&checkOut=2026-09-04&adults=2&children=0")
                .contains("\"totalAmountIncludingTax\":null", "\"taxAmount\":null");
    }

    @Test
    void 모든_공급사가_실패하면_503으로_나간다() {
        given(searchService.search(any(AvailabilityQuery.class))).willReturn(Mono.just(
                new SearchResponse(List.of(), List.of(SupplierStatus.failed(
                        Supplier.A, SupplierStatus.Status.SUPPLIER_ERROR, "boom")))));

        assertThat(mvc.get().uri(SEARCH + "?checkIn=2026-09-01&checkOut=2026-09-04&adults=2&children=0"))
                .hasStatus(503);
    }

    @Test
    void 전체_실패_응답에는_code와_suppliers가_담긴다() {
        given(searchService.search(any(AvailabilityQuery.class))).willReturn(Mono.just(
                new SearchResponse(List.of(), List.of(SupplierStatus.failed(
                        Supplier.A, SupplierStatus.Status.SUPPLIER_ERROR, "boom")))));

        assertThat(mvc.get().uri(SEARCH + "?checkIn=2026-09-01&checkOut=2026-09-04&adults=2&children=0"))
                .bodyText().contains("\"code\":\"ALL_SUPPLIERS_FAILED\"", "\"suppliers\":");
    }

    @Test
    void 체크아웃이_체크인보다_앞서면_400으로_나간다() {
        respondWith(okResponse());

        assertThat(mvc.get().uri(SEARCH + "?checkIn=2026-09-04&checkOut=2026-09-01&adults=2&children=0"))
                .hasStatus(400);
    }

    @Test
    void 없는_날짜를_보내면_400으로_나간다() {
        respondWith(okResponse());

        assertThat(mvc.get().uri(SEARCH + "?checkIn=2026-13-45&checkOut=2026-09-04&adults=2&children=0"))
                .hasStatus(400);
    }

    @Test
    void 날짜_형식이_아닌_값을_보내면_400으로_나간다() {
        respondWith(okResponse());

        assertThat(mvc.get().uri(SEARCH + "?checkIn=abc&checkOut=2026-09-04&adults=2&children=0"))
                .hasStatus(400);
    }

    @Test
    void 필수_파라미터가_빠지면_400으로_나간다() {
        respondWith(okResponse());

        assertThat(mvc.get().uri(SEARCH + "?checkIn=2026-09-01&checkOut=2026-09-04&adults=2"))
                .hasStatus(400);
    }
}
