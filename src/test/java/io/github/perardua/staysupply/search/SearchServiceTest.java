package io.github.perardua.staysupply.search;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import io.github.perardua.staysupply.adapter.AvailabilityQuery;
import io.github.perardua.staysupply.adapter.PropertyListing;
import io.github.perardua.staysupply.adapter.SupplierClient;
import io.github.perardua.staysupply.adapter.SupplierClientErrorException;
import io.github.perardua.staysupply.adapter.SupplierClientException;
import io.github.perardua.staysupply.adapter.SupplierOffer;
import io.github.perardua.staysupply.adapter.SupplierPoolExhaustedException;
import io.github.perardua.staysupply.adapter.SupplierServerException;
import io.github.perardua.staysupply.adapter.SupplierTimeoutException;
import io.github.perardua.staysupply.supplier.Supplier;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 청크 여러 개의 결과를 공급사 상태 하나로 줄이는 규칙(F)과,
 * 공급사 하나가 죽어도 나머지로 응답하는 규칙(G)을 고정한다.
 * 둘 다 응답은 정상으로 보이므로 어긋나도 실행으로는 드러나지 않는다.
 */
class SearchServiceTest {

    private static final LocalDate CHECK_IN = LocalDate.of(2026, 9, 1);
    private static final AvailabilityQuery QUERY =
            new AvailabilityQuery(CHECK_IN, CHECK_IN.plusDays(1), 2, 0);

    // 청크 상한이 50이므로 120개는 50/50/20 세 청크가 된다.
    private static final int THREE_CHUNKS = 120;

    // ── 조립 ────────────────────────────────────────────────────

    private static String code(Supplier supplier, int index) {
        return "%s-%03d".formatted(supplier, index);
    }

    private static List<RoomTypeMapping> mappings(Supplier supplier, int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new RoomTypeMapping(supplier, code(supplier, i), i, "R", 1000L + i))
                .toList();
    }

    private static SearchService service(List<RoomTypeMapping> mappings, SupplierClient... clients) {
        return service(mappings, 5000, clients);
    }

    private static SearchService service(List<RoomTypeMapping> mappings, long deadlineMillis,
                                         SupplierClient... clients) {
        return new SearchService(
                List.of(clients),
                new StubSearchRepository(mappings),
                new SearchProperties(10, deadlineMillis),
                CircuitBreakerRegistry.ofDefaults());
    }

    private static SupplierOffer offer(Supplier supplier, int index) {
        return new SupplierOffer(code(supplier, index), "Stay " + index, "R", "Room",
                2, 1, false, "KRW", 100000L, 10000L);
    }

    /** 청크의 첫 코드 번호로 그 청크가 몇 번째인지 알아낸다. */
    private static int chunkIndex(List<String> chunk) {
        String first = chunk.get(0);
        return Integer.parseInt(first.substring(first.indexOf('-') + 1)) / 50;
    }

    private static SupplierStatus statusOf(SearchResponse response, Supplier supplier) {
        return response.suppliers().stream()
                .filter(s -> s.supplier() == supplier)
                .findFirst()
                .orElseThrow();
    }

    private static Mono<List<SupplierOffer>> failed(SupplierClientException e) {
        return Mono.error(e);
    }

    private static Mono<List<SupplierOffer>> okFrom(Supplier supplier) {
        return Mono.just(List.of(offer(supplier, 0)));
    }

    // ── F. 청크 결과가 섞였을 때의 대표 상태 ──────────────────────

    private static SearchResponse searchWithChunkOutcomes(
            Function<Integer, Mono<List<SupplierOffer>>> byChunk) {
        SupplierClient client = new StubSupplierClient(Supplier.A,
                chunk -> byChunk.apply(chunkIndex(chunk)));
        return service(mappings(Supplier.A, THREE_CHUNKS), client).search(QUERY).block();
    }

    @Test
    void 청크가_모두_성공하면_공급사_상태는_OK다() {
        SearchResponse response = searchWithChunkOutcomes(chunk -> okFrom(Supplier.A));

        assertThat(statusOf(response, Supplier.A).status()).isEqualTo(SupplierStatus.Status.OK);
    }

    @Test
    void 청크가_모두_같은_이유로_실패하면_그_이유가_공급사_상태가_된다() {
        SearchResponse response = searchWithChunkOutcomes(chunk ->
                failed(new SupplierServerException(Supplier.A, "boom", null)));

        assertThat(statusOf(response, Supplier.A).status())
                .isEqualTo(SupplierStatus.Status.SUPPLIER_ERROR);
    }

    @Test
    void 성공한_청크와_실패한_청크가_섞이면_공급사_상태는_PARTIAL이다() {
        SearchResponse response = searchWithChunkOutcomes(chunk -> chunk == 0
                ? failed(new SupplierServerException(Supplier.A, "boom", null))
                : okFrom(Supplier.A));

        assertThat(statusOf(response, Supplier.A).status())
                .isEqualTo(SupplierStatus.Status.PARTIAL);
    }

    @Test
    void 청크_일부가_실패해도_성공한_청크의_상품은_응답에_담긴다() {
        SearchResponse response = searchWithChunkOutcomes(chunk -> chunk == 0
                ? failed(new SupplierServerException(Supplier.A, "boom", null))
                : okFrom(Supplier.A));

        assertThat(response.offers()).isNotEmpty();
    }

    @Test
    void 서로_다른_이유로_모두_실패하면_우선순위가_높은_쪽이_공급사_상태가_된다() {
        // TIMEOUT이 SUPPLIER_ERROR보다 앞선다.
        SearchResponse response = searchWithChunkOutcomes(chunk -> chunk == 0
                ? failed(new SupplierTimeoutException(Supplier.A, "slow", null))
                : failed(new SupplierServerException(Supplier.A, "boom", null)));

        assertThat(statusOf(response, Supplier.A).status())
                .isEqualTo(SupplierStatus.Status.TIMEOUT);
    }

    @Test
    void 커넥션_풀_포화가_섞이면_다른_실패보다_먼저_공급사_상태가_된다() {
        // 우리 쪽 자원 부족이 다른 실패들의 원인일 수 있어 맨 앞에 둔다.
        SearchResponse response = searchWithChunkOutcomes(chunk -> switch (chunk) {
            case 0 -> failed(new SupplierTimeoutException(Supplier.A, "slow", null));
            case 1 -> failed(new SupplierPoolExhaustedException(Supplier.A, "no connection", null));
            default -> failed(new SupplierClientErrorException(Supplier.A, "rejected", null));
        });

        assertThat(statusOf(response, Supplier.A).status())
                .isEqualTo(SupplierStatus.Status.POOL_EXHAUSTED);
    }

    @Test
    void 데드라인_안에_응답하지_못한_청크는_실패로_센다() {
        // 잘린 청크를 세지 않으면 도착한 청크만으로 OK가 되어, 숙소가 빠진 결과가 완전한 결과로 보인다.
        SupplierClient client = new StubSupplierClient(Supplier.A, chunk ->
                chunkIndex(chunk) == 2 ? Mono.never() : okFrom(Supplier.A));
        SearchResponse response =
                service(mappings(Supplier.A, THREE_CHUNKS), 300, client).search(QUERY).block();

        assertThat(statusOf(response, Supplier.A).status())
                .isEqualTo(SupplierStatus.Status.PARTIAL);
    }

    // ── G. 공급사 하나가 죽어도 나머지로 응답한다 ──────────────────

    private static SearchResponse searchBothSuppliers(Mono<List<SupplierOffer>> fromA,
                                                      Mono<List<SupplierOffer>> fromB) {
        List<RoomTypeMapping> mappings = List.of(
                new RoomTypeMapping(Supplier.A, code(Supplier.A, 0), 1L, "R", 11L),
                new RoomTypeMapping(Supplier.B, code(Supplier.B, 0), 2L, "R", 22L));

        return service(mappings,
                new StubSupplierClient(Supplier.A, chunk -> fromA),
                new StubSupplierClient(Supplier.B, chunk -> fromB))
                .search(QUERY).block();
    }

    @Test
    void 두_공급사가_모두_성공하면_양쪽_상품이_모두_응답에_담긴다() {
        SearchResponse response = searchBothSuppliers(okFrom(Supplier.A), okFrom(Supplier.B));

        assertThat(response.offers()).extracting(StayOffer::supplier)
                .containsExactlyInAnyOrder(Supplier.A, Supplier.B);
    }

    @Test
    void A가_실패하고_B가_성공하면_B의_상품만_응답에_담긴다() {
        SearchResponse response = searchBothSuppliers(
                failed(new SupplierServerException(Supplier.A, "boom", null)), okFrom(Supplier.B));

        assertThat(response.offers()).extracting(StayOffer::supplier).containsExactly(Supplier.B);
    }

    @Test
    void A가_실패하면_A의_실패_원인이_응답에_남는다() {
        SearchResponse response = searchBothSuppliers(
                failed(new SupplierServerException(Supplier.A, "boom", null)), okFrom(Supplier.B));

        assertThat(statusOf(response, Supplier.A).status())
                .isEqualTo(SupplierStatus.Status.SUPPLIER_ERROR);
    }

    @Test
    void B가_실패하고_A가_성공하면_A의_상품만_응답에_담긴다() {
        SearchResponse response = searchBothSuppliers(
                okFrom(Supplier.A), failed(new SupplierServerException(Supplier.B, "boom", null)));

        assertThat(response.offers()).extracting(StayOffer::supplier).containsExactly(Supplier.A);
    }

    @Test
    void B가_실패하면_B의_실패_원인이_응답에_남는다() {
        SearchResponse response = searchBothSuppliers(
                okFrom(Supplier.A), failed(new SupplierServerException(Supplier.B, "boom", null)));

        assertThat(statusOf(response, Supplier.B).status())
                .isEqualTo(SupplierStatus.Status.SUPPLIER_ERROR);
    }

    @Test
    void 두_공급사가_모두_실패하면_전체_실패로_판정된다() {
        SearchResponse response = searchBothSuppliers(
                failed(new SupplierServerException(Supplier.A, "boom", null)),
                failed(new SupplierTimeoutException(Supplier.B, "slow", null)));

        assertThat(response.allSuppliersFailed()).isTrue();
    }

    @Test
    void 한_공급사만_실패하면_전체_실패로_판정되지_않는다() {
        SearchResponse response = searchBothSuppliers(
                failed(new SupplierServerException(Supplier.A, "boom", null)), okFrom(Supplier.B));

        assertThat(response.allSuppliersFailed()).isFalse();
    }

    @Test
    void 모든_공급사가_PARTIAL이면_전체_실패로_보지_않는다() {
        // PARTIAL은 성공한 청크의 상품이 응답에 들어 있다는 뜻이다. 이때 503을 내면 있는 결과를 버린다.
        List<RoomTypeMapping> mappings = new ArrayList<>(mappings(Supplier.A, THREE_CHUNKS));
        mappings.addAll(mappings(Supplier.B, THREE_CHUNKS));
        Function<List<String>, Mono<List<SupplierOffer>>> firstChunkFails = chunk ->
                chunkIndex(chunk) == 0
                        ? failed(new SupplierServerException(Supplier.A, "boom", null))
                        : okFrom(Supplier.A);

        SearchResponse response = service(mappings,
                new StubSupplierClient(Supplier.A, firstChunkFails),
                new StubSupplierClient(Supplier.B, firstChunkFails))
                .search(QUERY).block();

        assertThat(response.allSuppliersFailed()).isFalse();
    }

    @Test
    void 한_숙소에_객실이_여러_개여도_숙소_코드는_한_번만_공급사에_보낸다() {
        // 매핑은 객실 타입마다 한 줄이라 같은 숙소 코드가 객실 수만큼 반복된다.
        // 그대로 보내면 청크 수가 배로 늘어 공급사 호출과 데드라인 소모가 함께 커진다.
        List<RoomTypeMapping> mappings = new ArrayList<>();
        for (int property = 0; property < 3; property++) {
            for (int roomType = 0; roomType < 3; roomType++) {
                mappings.add(new RoomTypeMapping(Supplier.A, code(Supplier.A, property),
                        property, "R-" + roomType, property * 10L + roomType));
            }
        }
        List<String> sent = new ArrayList<>();

        service(mappings, new StubSupplierClient(Supplier.A, chunk -> {
            sent.addAll(chunk);
            return Mono.just(List.of());
        })).search(QUERY).block();

        assertThat(sent).containsExactly(
                code(Supplier.A, 0), code(Supplier.A, 1), code(Supplier.A, 2));
    }

    // ── 스텁 ────────────────────────────────────────────────────

    private record StubSupplierClient(Supplier supplier,
                                      Function<List<String>, Mono<List<SupplierOffer>>> behavior)
            implements SupplierClient {

        @Override
        public List<PropertyListing> fetchProperties() {
            throw new UnsupportedOperationException("검색 경로에서는 부르지 않는다");
        }

        @Override
        public Mono<List<SupplierOffer>> fetchAvailability(List<String> supplierCodes,
                                                           AvailabilityQuery query) {
            return behavior.apply(supplierCodes);
        }
    }

    private static final class StubSearchRepository extends SearchRepository {

        private final List<RoomTypeMapping> mappings;

        private StubSearchRepository(List<RoomTypeMapping> mappings) {
            super(null);
            this.mappings = mappings;
        }

        @Override
        public List<RoomTypeMapping> findActiveMappings() {
            return mappings;
        }
    }
}
