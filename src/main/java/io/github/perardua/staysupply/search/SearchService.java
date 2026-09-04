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
import io.github.perardua.staysupply.adapter.SupplierOffer;
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

        List<SupplierStatus> suppliers = queried.stream()
                .map(client -> SupplierStatus.ok(client.supplier()))
                .toList();

        List<Mono<SupplierOffers>> calls = queried.stream()
                .map(client -> client
                        .fetchAvailability(codesBySupplier.get(client.supplier()), query)
                        .map(offers -> new SupplierOffers(client.supplier(), offers)))
                .toList();

        if (calls.isEmpty()) {
            return Mono.just(new SearchResponse(List.of(), suppliers));
        }

        return Mono.zip(calls, results -> toResponse(results, mappingByKey, suppliers));
    }

    private SearchResponse toResponse(Object[] results,
                                      Map<MappingKey, RoomTypeMapping> mappingByKey,
                                      List<SupplierStatus> suppliers) {
        List<StayOffer> offers = new ArrayList<>();
        Set<MappingKey> unmapped = new LinkedHashSet<>();

        for (Object result : results) {
            SupplierOffers supplierOffers = (SupplierOffers) result;
            for (SupplierOffer offer : supplierOffers.offers()) {
                MappingKey key = MappingKey.of(supplierOffers.supplier(), offer);
                RoomTypeMapping mapping = mappingByKey.get(key);
                if (mapping == null) {
                    unmapped.add(key);
                    continue;
                }
                offers.add(toStayOffer(offer, mapping, supplierOffers.supplier()));
            }
        }

        if (!unmapped.isEmpty()) {
            log.warn("search dropped {} offer(s) with no active mapping, sample={}",
                    unmapped.size(), unmapped.stream().limit(UNMAPPED_LOG_SAMPLE).toList());
        }

        return new SearchResponse(List.copyOf(offers), suppliers);
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

    private record SupplierOffers(Supplier supplier, List<SupplierOffer> offers) {}

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
