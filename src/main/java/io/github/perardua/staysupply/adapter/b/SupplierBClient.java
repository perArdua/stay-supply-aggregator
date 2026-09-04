package io.github.perardua.staysupply.adapter.b;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.springframework.core.codec.DecodingException;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import io.github.perardua.staysupply.adapter.PropertyListing;
import io.github.perardua.staysupply.adapter.RoomTypeListing;
import io.github.perardua.staysupply.adapter.SupplierClient;
import io.github.perardua.staysupply.adapter.SupplierClientErrorException;
import io.github.perardua.staysupply.adapter.SupplierClientException;
import io.github.perardua.staysupply.adapter.SupplierMalformedException;
import io.github.perardua.staysupply.adapter.SupplierOffer;
import io.github.perardua.staysupply.adapter.SupplierServerException;
import io.github.perardua.staysupply.adapter.SupplierTimeoutException;
import io.github.perardua.staysupply.search.SearchQuery;
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
    public Mono<List<SupplierOffer>> fetchAvailability(List<String> supplierCodes, SearchQuery query) {
        // TODO: 다음 단계에서 구현
        throw new UnsupportedOperationException("fetchAvailability is not implemented yet");
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

    record BData(List<BProperty> items) {}

    record BProperty(String propertyId, String propertyName, List<BRoom> rooms) {}

    record BRoom(String roomId, String roomName, int maxOccupancy) {}
}
