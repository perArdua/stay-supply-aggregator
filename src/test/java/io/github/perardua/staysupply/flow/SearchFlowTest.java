package io.github.perardua.staysupply.flow;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import io.github.perardua.staysupply.adapter.AvailabilityQuery;
import io.github.perardua.staysupply.adapter.SupplierOffer;
import io.github.perardua.staysupply.adapter.SupplierServerException;
import io.github.perardua.staysupply.adapter.a.SupplierAClient;
import io.github.perardua.staysupply.adapter.b.SupplierBClient;
import io.github.perardua.staysupply.supplier.Supplier;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

/**
 * 공급사 응답에서 고객 응답까지 중간 계층을 실제로 통과시킨다.
 * 공급사 호출만 대역으로 바꾸고 매핑 조회, 병합, 직렬화는 실제 빈이 한다.
 *
 */
@SpringBootTest
@AutoConfigureMockMvc
@Tag("docker")
class SearchFlowTest {

    private static final String SEARCH =
            "/api/v1/stays/search?checkIn=2026-09-01&checkOut=2026-09-02&adults=2&children=0";

    /** 이 테스트만 쓰는 코드. 다른 데이터와 섞이지 않도록 접두어로 구분한다. */
    private static final String A_CODE = "IT-A-1";
    private static final String B_CODE = "IT-B-1";
    private static final String ROOM_CODE = "IT-ROOM";
    private static final String UNMAPPED_CODE = "IT-A-UNMAPPED";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbcClient;

    @MockitoBean
    SupplierAClient supplierAClient;

    @MockitoBean
    SupplierBClient supplierBClient;

    private long aPropertyId;
    private long aRoomTypeId;
    private long bPropertyId;
    private long bRoomTypeId;

    @BeforeEach
    void seedMappings() {
        given(supplierAClient.supplier()).willReturn(Supplier.A);
        given(supplierBClient.supplier()).willReturn(Supplier.B);

        deleteSeed();
        aPropertyId = insertProperty(Supplier.A, A_CODE);
        aRoomTypeId = insertRoomType(aPropertyId);
        bPropertyId = insertProperty(Supplier.B, B_CODE);
        bRoomTypeId = insertRoomType(bPropertyId);
    }

    @AfterEach
    void removeMappings() {
        deleteSeed();
    }

    private void supplierAReturns(Mono<List<SupplierOffer>> response) {
        given(supplierAClient.fetchAvailability(anyList(), any(AvailabilityQuery.class))).willReturn(response);
    }

    private void supplierBReturns(Mono<List<SupplierOffer>> response) {
        given(supplierBClient.fetchAvailability(anyList(), any(AvailabilityQuery.class))).willReturn(response);
    }

    // ── 시나리오 ──────────────────────────────────────────────────

    @Test
    void A의_합산_요금과_B의_공급사_총액이_그대로_실려_나간다() {
        supplierAReturns(Mono.just(List.of(offer(A_CODE, "Riverside", 429000L, 39000L))));
        supplierBReturns(Mono.just(List.of(offer(B_CODE, "Riverside", 452000L, null))));

        assertThat(mvc.get().uri(SEARCH)).hasStatusOk().bodyText()
                .contains("\"totalAmountIncludingTax\":429000,\"taxAmount\":39000",
                        "\"totalAmountIncludingTax\":452000,\"taxAmount\":null");
    }

    @Test
    void 공급사_코드는_내부_식별자로_바뀌어_나간다() {
        // 공급사 코드가 응답에 새면 내부 식별자로 통합한 의미가 사라진다.
        supplierAReturns(Mono.just(List.of(offer(A_CODE, "Riverside", 429000L, 39000L))));
        supplierBReturns(Mono.just(List.of(offer(B_CODE, "Riverside", 452000L, null))));

        assertThat(mvc.get().uri(SEARCH)).hasStatusOk().bodyText()
                .contains("\"propertyId\":" + aPropertyId + ",", "\"roomTypeId\":" + aRoomTypeId + ",",
                        "\"propertyId\":" + bPropertyId + ",", "\"roomTypeId\":" + bRoomTypeId + ",")
                .doesNotContain(A_CODE, B_CODE, ROOM_CODE);
    }

    @Test
    void A가_실패해도_B의_상품으로_200을_돌려준다() {
        supplierAReturns(Mono.error(new SupplierServerException(Supplier.A, "boom", null)));
        supplierBReturns(Mono.just(List.of(offer(B_CODE, "Riverside", 452000L, null))));

        assertThat(mvc.get().uri(SEARCH)).hasStatusOk().bodyText()
                .contains("\"propertyId\":" + bPropertyId + ",")
                .doesNotContain("\"propertyId\":" + aPropertyId + ",");
    }

    @Test
    void A가_실패하면_그_사실이_응답의_공급사_상태에_남는다() {
        supplierAReturns(Mono.error(new SupplierServerException(Supplier.A, "boom", null)));
        supplierBReturns(Mono.just(List.of(offer(B_CODE, "Riverside", 452000L, null))));

        assertThat(mvc.get().uri(SEARCH)).hasStatusOk().bodyText()
                .contains("\"supplier\":\"A\",\"status\":\"SUPPLIER_ERROR\"");
    }

    @Test
    void 매핑에_없는_상품만_빠지고_나머지는_그대로_나간다() {
        supplierAReturns(Mono.just(List.of(
                offer(UNMAPPED_CODE, "Unknown", 100000L, 10000L),
                offer(A_CODE, "Riverside", 429000L, 39000L))));
        supplierBReturns(Mono.just(List.of(offer(B_CODE, "Riverside", 452000L, null))));

        assertThat(mvc.get().uri(SEARCH)).hasStatusOk().bodyText()
                .contains("\"propertyId\":" + aPropertyId + ",", "\"propertyId\":" + bPropertyId + ",")
                .doesNotContain("Unknown");
    }

    // ── 매핑 데이터 ───────────────────────────────────────────────

    private long insertProperty(Supplier supplier, String code) {
        jdbcClient.sql("""
                        INSERT INTO supplier_property (supplier, supplier_code, active, last_synced_at)
                        VALUES (:supplier, :code, 1, :now)
                        """)
                .param("supplier", supplier.name()).param("code", code)
                .param("now", LocalDateTime.now(ZoneOffset.UTC)).update();
        return jdbcClient.sql("SELECT id FROM supplier_property WHERE supplier = :s AND supplier_code = :c")
                .param("s", supplier.name()).param("c", code).query(Long.class).single();
    }

    private long insertRoomType(long propertyId) {
        jdbcClient.sql("""
                        INSERT INTO supplier_room_type (property_id, supplier_room_code, active, last_synced_at)
                        VALUES (:propertyId, :code, 1, :now)
                        """)
                .param("propertyId", propertyId).param("code", ROOM_CODE)
                .param("now", LocalDateTime.now(ZoneOffset.UTC)).update();
        return jdbcClient.sql("SELECT id FROM supplier_room_type WHERE property_id = :p")
                .param("p", propertyId).query(Long.class).single();
    }

    private void deleteSeed() {
        jdbcClient.sql("""
                DELETE rt FROM supplier_room_type rt
                JOIN supplier_property p ON p.id = rt.property_id
                WHERE p.supplier_code LIKE 'IT-%'
                """).update();
        jdbcClient.sql("DELETE FROM supplier_property WHERE supplier_code LIKE 'IT-%'").update();
    }

    private static SupplierOffer offer(String propertyCode, String propertyName, Long total, Long tax) {
        return new SupplierOffer(propertyCode, propertyName, ROOM_CODE, "Deluxe Twin",
                2, 1, false, "KRW", total, tax);
    }
}
