package io.github.perardua.staysupply.adapter.a;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.springframework.core.codec.DecodingException;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import io.github.perardua.staysupply.adapter.PropertyListing;
import io.github.perardua.staysupply.adapter.RoomTypeListing;
import io.github.perardua.staysupply.adapter.SupplierClient;
import io.github.perardua.staysupply.adapter.SupplierClientErrorException;
import io.github.perardua.staysupply.adapter.SupplierClientException;
import io.github.perardua.staysupply.adapter.SupplierMalformedException;
import io.github.perardua.staysupply.adapter.SupplierServerException;
import io.github.perardua.staysupply.adapter.SupplierTimeoutException;
import io.github.perardua.staysupply.supplier.Supplier;
import reactor.core.publisher.Mono;

@Component
public class SupplierAClient implements SupplierClient {

    private final WebClient webClient;
    private final Duration overallTimeout;

    public SupplierAClient(WebClient supplierAWebClient, SupplierAProperties properties) {
        this.webClient = supplierAWebClient;
        this.overallTimeout = Duration.ofMillis(properties.overallTimeoutMillis());
    }

    @Override
    public Supplier supplier() {
        return Supplier.A;
    }

    @Override
    public List<PropertyListing> fetchProperties() {
        AHotelsResponse response;
        try {
            response = webClient.get()
                    .uri("/a/v1/hotels")
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, r -> Mono.error(new SupplierClientErrorException(
                            Supplier.A, "hotel API responded " + r.statusCode(), null)))
                    .onStatus(HttpStatusCode::is5xxServerError, r -> Mono.error(new SupplierServerException(
                            Supplier.A, "hotel API responded " + r.statusCode(), null)))
                    .bodyToMono(AHotelsResponse.class)
                    .timeout(overallTimeout)
                    .block();
        } catch (SupplierClientException e) {
            throw e;
        } catch (Exception e) {
            // block()은 검사 예외를 ReactiveException으로 감싸므로 원인 사슬을 따라간다.
            if (hasCause(e, TimeoutException.class)) {
                throw new SupplierTimeoutException(Supplier.A, "hotels API timed out", e);
            }
            if (hasCause(e, DecodingException.class)) {
                throw new SupplierMalformedException(Supplier.A, "hotels API response could not be decoded", e);
            }
            throw new SupplierServerException(Supplier.A, "hotels API call failed", e);
        }
        if (response == null || response.items() == null) {
            return List.of();
        }
        return response.items().stream().map(this::toListing).toList();
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

    private PropertyListing toListing(AHotel hotel) {
        List<RoomTypeListing> roomTypes = hotel.roomTypes() == null
                ? List.of()
                : hotel.roomTypes().stream()
                .map(r -> new RoomTypeListing(
                        r.roomTypeCode(), r.roomTypeName(), r.maxOccupancy()))
                .toList();
        return new PropertyListing(hotel.hotelCode(), hotel.hotelName(), roomTypes);
    }

    record AHotelsResponse(List<AHotel> items) {}

    record AHotel(String hotelCode, String hotelName, List<ARoomType> roomTypes) {}

    record ARoomType(String roomTypeCode, String roomTypeName, int maxOccupancy) {}
}
