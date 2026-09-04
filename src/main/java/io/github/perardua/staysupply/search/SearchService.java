package io.github.perardua.staysupply.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.perardua.staysupply.adapter.AvailabilityQuery;
import io.github.perardua.staysupply.adapter.SupplierClient;
import io.github.perardua.staysupply.adapter.SupplierClientErrorException;
import io.github.perardua.staysupply.adapter.SupplierClientException;
import io.github.perardua.staysupply.adapter.SupplierMalformedException;
import io.github.perardua.staysupply.adapter.SupplierOffer;
import io.github.perardua.staysupply.adapter.SupplierServerException;
import io.github.perardua.staysupply.adapter.SupplierTimeoutException;
import io.github.perardua.staysupply.supplier.Supplier;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    // 매핑이 어긋나면 누락 항목이 응답 크기만큼 나올 수 있어 로그 한 줄이 비대해져서 상한을 둠
    private static final int UNMAPPED_LOG_SAMPLE = 10;

    // 공급사 재고 요금 API가 한 번에 받는 숙소 코드 수의 상한. 넘기면 공급사가 요청을 거부한다.
    private static final int MAX_CODES_PER_CALL = 50;

    // 청크가 전부 실패했을 때 공급사 상태로 올릴 순서. 앞일수록 우선한다.
    private static final List<SupplierStatus.Status> FAILURE_PRECEDENCE = List.of(
            SupplierStatus.Status.REQUEST_REJECTED,
            SupplierStatus.Status.MALFORMED_RESPONSE,
            SupplierStatus.Status.TIMEOUT,
            SupplierStatus.Status.SUPPLIER_ERROR);

    private final List<SupplierClient> supplierClientList;
    private final SearchRepository searchRepository;
    private final SearchProperties searchProperties;

    public SearchService(List<SupplierClient> supplierClientList,
                         SearchRepository searchRepository,
                         SearchProperties searchProperties) {
        this.supplierClientList = supplierClientList;
        this.searchRepository = searchRepository;
        this.searchProperties = searchProperties;
    }

    public Mono<SearchResponse> search(AvailabilityQuery query) {
        List<RoomTypeMapping> mappings = searchRepository.findActiveMappings();

        Map<Supplier, List<String>> codesBySupplier = mappings.stream()
                .collect(Collectors.groupingBy(
                        RoomTypeMapping::supplier,
                        Collectors.mapping(RoomTypeMapping::supplierPropertyCode,
                                Collectors.collectingAndThen(
                                        Collectors.toCollection(LinkedHashSet::new), List::copyOf))));

        Map<MappingKey, RoomTypeMapping> mappingByKey = mappings.stream()
                .collect(Collectors.toMap(MappingKey::of, mapping -> mapping));

        List<SupplierClient> queried = supplierClientList.stream()
                .filter(client -> codesBySupplier.containsKey(client.supplier()))
                .toList();

        List<Mono<SupplierResult>> calls = queried.stream()
                .map(client -> callSupplier(client, codesBySupplier.get(client.supplier()), query))
                .toList();

        if (calls.isEmpty()) {
            return Mono.just(new SearchResponse(List.of(), List.of()));
        }

        return Mono.zip(calls, results -> toResponse(results, mappingByKey));
    }

    private Mono<SupplierResult> callSupplier(SupplierClient client, List<String> codes, AvailabilityQuery query) {
        List<List<String>> chunks = chunk(codes);

        // flatMapSequential은 동시에 호출하되 결과는 청크 순서대로 내보낸다.
        return Flux.fromIterable(chunks)
                .flatMapSequential(chunk -> client.fetchAvailability(chunk, query)
                                .map(ChunkResult::ok)
                                .onErrorResume(SupplierClientException.class,
                                        e -> Mono.just(ChunkResult.failed(e))),
                        searchProperties.maxConcurrentCallsPerSupplier())
                .collectList()
                .map(chunkResults -> merge(client.supplier(), chunkResults));
    }

    private static List<List<String>> chunk(List<String> codes) {
        List<List<String>> chunks = new ArrayList<>();
        for (int from = 0; from < codes.size(); from += MAX_CODES_PER_CALL) {
            chunks.add(codes.subList(from, Math.min(from + MAX_CODES_PER_CALL, codes.size())));
        }
        return chunks;
    }

    private SupplierResult merge(Supplier supplier, List<ChunkResult> chunkResults) {
        List<SupplierOffer> offers = new ArrayList<>();
        List<SupplierClientException> failures = new ArrayList<>();
        for (ChunkResult chunkResult : chunkResults) {
            if (chunkResult.failure() == null) {
                offers.addAll(chunkResult.offers());
            } else {
                failures.add(chunkResult.failure());
            }
        }

        if (failures.isEmpty()) {
            return new SupplierResult(supplier, offers, SupplierStatus.ok(supplier));
        }

        SupplierClientException worst = worst(failures);
        String message = "%d of %d chunks failed (%s): %s"
                .formatted(failures.size(), chunkResults.size(), statusOf(worst), worst.getMessage());

        // 하나라도 성공했으면 그 결과는 버리지 않는다.
        if (failures.size() < chunkResults.size()) {
            return new SupplierResult(supplier, offers,
                    SupplierStatus.failed(supplier, SupplierStatus.Status.PARTIAL, message));
        }
        return new SupplierResult(supplier, List.of(),
                SupplierStatus.failed(supplier, statusOf(worst), message));
    }

    private static SupplierClientException worst(List<SupplierClientException> failures) {
        return failures.stream()
                .min(Comparator.comparingInt(e -> FAILURE_PRECEDENCE.indexOf(statusOf(e))))
                .orElseThrow();
    }

    // sealed이므로 default를 두지 않는다. 하위 타입이 늘면 컴파일 에러로 드러난다.
    private static SupplierStatus.Status statusOf(SupplierClientException e) {
        return switch (e) {
            case SupplierTimeoutException ignored -> SupplierStatus.Status.TIMEOUT;
            case SupplierServerException ignored -> SupplierStatus.Status.SUPPLIER_ERROR;
            case SupplierClientErrorException ignored -> SupplierStatus.Status.REQUEST_REJECTED;
            case SupplierMalformedException ignored -> SupplierStatus.Status.MALFORMED_RESPONSE;
        };
    }

    private SearchResponse toResponse(Object[] results, Map<MappingKey, RoomTypeMapping> mappingByKey) {
        List<StayOffer> offers = new ArrayList<>();
        List<SupplierStatus> suppliers = new ArrayList<>();
        Set<MappingKey> unmapped = new LinkedHashSet<>();

        for (Object result : results) {
            SupplierResult supplierResult = (SupplierResult) result;
            suppliers.add(supplierResult.status());
            for (SupplierOffer offer : supplierResult.offers()) {
                MappingKey key = MappingKey.of(supplierResult.supplier(), offer);
                RoomTypeMapping mapping = mappingByKey.get(key);
                if (mapping == null) {
                    unmapped.add(key);
                    continue;
                }
                offers.add(toStayOffer(offer, mapping, supplierResult.supplier()));
            }
        }

        if (!unmapped.isEmpty()) {
            log.warn("search dropped {} offer(s) with no active mapping, sample={}",
                    unmapped.size(), unmapped.stream().limit(UNMAPPED_LOG_SAMPLE).toList());
        }

        return new SearchResponse(List.copyOf(offers), List.copyOf(suppliers));
    }

    private StayOffer toStayOffer(SupplierOffer offer, RoomTypeMapping mapping, Supplier supplier) {
        return new StayOffer(
                mapping.propertyId(),
                offer.propertyName(),
                mapping.roomTypeId(),
                offer.roomTypeName(),
                offer.maxOccupancy(),
                offer.availableRooms(),
                offer.breakfastIncluded(),
                offer.currency(),
                offer.totalAmountIncludingTax(),
                offer.taxAmount(),
                supplier);
    }

    private record SupplierResult(Supplier supplier, List<SupplierOffer> offers, SupplierStatus status) {}

    private record ChunkResult(List<SupplierOffer> offers, SupplierClientException failure) {

        static ChunkResult ok(List<SupplierOffer> offers) {
            return new ChunkResult(offers, null);
        }

        static ChunkResult failed(SupplierClientException failure) {
            return new ChunkResult(List.of(), failure);
        }
    }

    private record MappingKey(Supplier supplier, String supplierPropertyCode, String supplierRoomTypeCode) {

        static MappingKey of(RoomTypeMapping mapping) {
            return new MappingKey(
                    mapping.supplier(), mapping.supplierPropertyCode(), mapping.supplierRoomTypeCode());
        }

        static MappingKey of(Supplier supplier, SupplierOffer offer) {
            return new MappingKey(supplier, offer.supplierPropertyCode(), offer.supplierRoomTypeCode());
        }
    }
}
