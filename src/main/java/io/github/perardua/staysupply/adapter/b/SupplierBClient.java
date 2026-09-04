package io.github.perardua.staysupply.adapter.b;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.springframework.core.codec.DecodingException;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import io.github.perardua.staysupply.adapter.AvailabilityQuery;
import io.github.perardua.staysupply.adapter.PropertyListing;
import io.github.perardua.staysupply.adapter.RoomTypeListing;
import io.github.perardua.staysupply.adapter.SupplierClient;
import io.github.perardua.staysupply.adapter.SupplierClientErrorException;
import io.github.perardua.staysupply.adapter.SupplierClientException;
import io.github.perardua.staysupply.adapter.SupplierMalformedException;
import io.github.perardua.staysupply.adapter.SupplierOffer;
import io.github.perardua.staysupply.adapter.SupplierServerException;
import io.github.perardua.staysupply.adapter.SupplierTimeoutException;
import io.github.perardua.staysupply.supplier.Supplier;
import reactor.core.publisher.Mono;

/**
 * B는 장애 상황에서도 HTTP 200을 내려준다.
 * 따라서 실패 판정은 상태 코드가 아니라 본문의 resultCode가 맡는다.
 */
@Component
public class SupplierBClient implements SupplierClient {

    private static final String RESULT_CODE_SUCCESS = "0000";

    private final WebClient webClient;
    private final Duration overallTimeout;

    public SupplierBClient(WebClient supplierBWebClient, SupplierBProperties properties) {
        this.webClient = supplierBWebClient;
        this.overallTimeout = Duration.ofMillis(properties.overallTimeoutMillis());
    }

    @Override
    public Supplier supplier() {
        return Supplier.B;
    }

    @Override
    public List<PropertyListing> fetchProperties() {
        BEnvelope envelope;
        try {
            envelope = webClient.get()
                    .uri("/b/api/properties")
                    .retrieve()
                    .bodyToMono(BEnvelope.class)
                    .timeout(overallTimeout)
                    .block();
        } catch (SupplierClientException e) {
            throw e;
        } catch (Exception e) {
            // block()은 검사 예외를 ReactiveException으로 감싸므로 원인 사슬을 따라간다.
            if (hasCause(e, TimeoutException.class)) {
                throw new SupplierTimeoutException(Supplier.B, "property API timed out", e);
            }
            if (hasCause(e, DecodingException.class)) {
                throw new SupplierMalformedException(Supplier.B, "property API response could not be decoded", e);
            }
            if (e instanceof WebClientResponseException responseException) {
                throw new SupplierServerException(Supplier.B,
                        "property API call failed with HTTP " + responseException.getStatusCode().value(), e);
            }
            throw new SupplierServerException(Supplier.B, "property API call failed", e);
        }

        if (envelope == null) {
            throw new SupplierMalformedException(Supplier.B, "property API returned an empty body", null);
        }

        verifyResultCode(envelope.resultCode(), envelope.resultMessage());

        // 성공 응답이 데이터를 안 주는 것은 "숙소가 없다"가 아니라 스펙 위반이다.
        if (envelope.data() == null || envelope.data().items() == null) {
            throw new SupplierMalformedException(
                    Supplier.B, "property API reported success but carried no data", null);
        }

        return envelope.data().items().stream().map(this::toListing).toList();
    }

    @Override
    public Mono<List<SupplierOffer>> fetchAvailability(List<String> supplierCodes, AvailabilityQuery query) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/b/api/search")
                        .queryParam("propertyIds", String.join(",", supplierCodes))
                        .queryParam("checkIn", query.checkIn())
                        .queryParam("checkOut", query.checkOut())
                        .queryParam("adults", query.adults())
                        .queryParam("children", query.children())
                        .build())
                .retrieve()
                .bodyToMono(BSearchEnvelope.class)
                .timeout(overallTimeout)
                // resultCode 검사를 map 안에 두어 실패가 구독 시점에 나도록 한다.
                .map(envelope -> toOffers(envelope, query))
                .switchIfEmpty(Mono.error(() -> new SupplierMalformedException(
                        Supplier.B, "search API returned an empty body", null)))
                .onErrorMap(e -> !(e instanceof SupplierClientException), this::toSupplierException);
    }

    private SupplierClientException toSupplierException(Throwable e) {
        if (hasCause(e, TimeoutException.class)) {
            return new SupplierTimeoutException(Supplier.B, "search API timed out", e);
        }
        if (hasCause(e, DecodingException.class)) {
            return new SupplierMalformedException(Supplier.B, "search API response could not be decoded", e);
        }
        if (e instanceof WebClientResponseException responseException) {
            return new SupplierServerException(Supplier.B,
                    "search API call failed with HTTP " + responseException.getStatusCode().value(), e);
        }
        return new SupplierServerException(Supplier.B, "search API call failed", e);
    }

    private List<SupplierOffer> toOffers(BSearchEnvelope envelope, AvailabilityQuery query) {
        verifyResultCode(envelope.resultCode(), envelope.resultMessage());

        // 성공 응답이 데이터를 안 주는 것은 "재고가 없다"가 아니라 스펙 위반이다.
        if (envelope.data() == null || envelope.data().items() == null) {
            throw new SupplierMalformedException(
                    Supplier.B, "search API reported success but carried no data", null);
        }

        return envelope.data().items().stream().map(item -> toOffer(item, query)).toList();
    }

    private SupplierOffer toOffer(BSearchItem item, AvailabilityQuery query) {
        List<BInventory> inventory = item.inventory() == null ? List.of() : item.inventory();

        return new SupplierOffer(
                item.propertyId(),
                item.propertyName(),
                item.roomId(),
                item.roomName(),
                item.maxOccupancy(),
                availableRooms(remainingRoomsByDate(item, inventory), query),
                item.breakfastIncluded(),
                item.currency(),
                item.totalPrice(),
                // B는 세금이 포함되어 있다는 것만 알리고 세액은 주지 않는다.
                // 0으로 채우면 세금이 없는 상품과 구분되지 않으므로 모른다는 뜻의 null을 남긴다.
                null);
    }

    private Map<LocalDate, Integer> remainingRoomsByDate(BSearchItem item, List<BInventory> inventory) {
        Map<LocalDate, Integer> remainingByDate = new HashMap<>();
        for (BInventory night : inventory) {
            if (night.date() == null) {
                continue;
            }
            if (remainingByDate.put(night.date(), night.remainingRooms()) != null) {
                throw new SupplierMalformedException(Supplier.B,
                        "search API returned duplicate date " + night.date()
                                + " for " + item.propertyId() + "/" + item.roomId(), null);
            }
        }
        return remainingByDate;
    }

    private int availableRooms(Map<LocalDate, Integer> remainingByDate, AvailabilityQuery query) {
        int minimum = Integer.MAX_VALUE;
        for (LocalDate night = query.checkIn(); night.isBefore(query.checkOut()); night = night.plusDays(1)) {
            minimum = Math.min(minimum, remainingByDate.getOrDefault(night, 0));
        }

        return minimum;
    }

    private void verifyResultCode(String resultCode, String resultMessage) {
        if (RESULT_CODE_SUCCESS.equals(resultCode)) {
            return;
        }

        String detail = "property API returned resultCode " + resultCode + " (" + resultMessage + ")";

        switch (resultCode == null ? "" : resultCode) {
            case "E400", "E401", "E429" ->
                    throw new SupplierClientErrorException(Supplier.B, detail, null);
            case "E500", "E503" ->
                    throw new SupplierServerException(Supplier.B, detail, null);
            default ->
                    throw new SupplierMalformedException(Supplier.B, "unknown " + detail, null);
        }
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable t = throwable; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    private PropertyListing toListing(BProperty property) {
        List<RoomTypeListing> roomTypes = property.rooms() == null
                ? List.of()
                : property.rooms().stream()
                .map(r -> new RoomTypeListing(
                        r.roomId(), r.roomName(), r.maxOccupancy()))
                .toList();
        return new PropertyListing(property.propertyId(), property.propertyName(), roomTypes);
    }

    record BEnvelope(String resultCode, String resultMessage, BData data) {}

    record BSearchEnvelope(String resultCode, String resultMessage, BSearchData data) {}

    record BSearchData(List<BSearchItem> items) {}

    record BSearchItem(
            String propertyId, String propertyName, String roomId, String roomName,
            int maxOccupancy, boolean breakfastIncluded, String currency,
            long totalPrice, List<BInventory> inventory) {}

    record BInventory(LocalDate date, int remainingRooms) {}

    record BData(List<BProperty> items) {}

    record BProperty(String propertyId, String propertyName, List<BRoom> rooms) {}

    record BRoom(String roomId, String roomName, int maxOccupancy) {}
}
