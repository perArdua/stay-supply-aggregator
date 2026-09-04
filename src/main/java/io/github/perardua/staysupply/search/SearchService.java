package io.github.perardua.staysupply.search;

import java.util.ArrayList;
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
import reactor.core.publisher.Mono;

public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    // 매핑이 어긋나면 누락 항목이 응답 크기만큼 나올 수 있어 로그 한 줄이 비대해져서 상한을 둠
    private static final int UNMAPPED_LOG_SAMPLE = 10;

    private final List<SupplierClient> supplierClientList;
    private final SearchRepository searchRepository;

    public SearchService(List<SupplierClient> supplierClientList, SearchRepository searchRepository) {
        this.supplierClientList = supplierClientList;
        this.searchRepository = searchRepository;
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
                .map(client -> client
                        .fetchAvailability(codesBySupplier.get(client.supplier()), query)
                        .map(offers -> SupplierResult.ok(client.supplier(), offers))
                        .onErrorResume(SupplierClientException.class,
                                e -> Mono.just(SupplierResult.failed(client.supplier(), e))))
                .toList();

        if (calls.isEmpty()) {
            return Mono.just(new SearchResponse(List.of(), List.of()));
        }

        return Mono.zip(calls, results -> toResponse(results, mappingByKey));
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

    private record SupplierResult(Supplier supplier, List<SupplierOffer> offers, SupplierStatus status) {

        static SupplierResult ok(Supplier supplier, List<SupplierOffer> offers) {
            return new SupplierResult(supplier, offers, SupplierStatus.ok(supplier));
        }

        static SupplierResult failed(Supplier supplier, SupplierClientException e) {
            return new SupplierResult(
                    supplier, List.of(), SupplierStatus.failed(supplier, statusOf(e), e.getMessage()));
        }

        private static SupplierStatus.Status statusOf(SupplierClientException e) {
            return switch (e) {
                case SupplierTimeoutException ignored -> SupplierStatus.Status.TIMEOUT;
                case SupplierServerException ignored -> SupplierStatus.Status.SUPPLIER_ERROR;
                case SupplierClientErrorException ignored -> SupplierStatus.Status.REQUEST_REJECTED;
                case SupplierMalformedException ignored -> SupplierStatus.Status.MALFORMED_RESPONSE;
            };
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
