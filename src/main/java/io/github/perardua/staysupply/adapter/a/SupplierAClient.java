package io.github.perardua.staysupply.adapter.a;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.springframework.core.codec.DecodingException;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import io.github.perardua.staysupply.adapter.AvailabilityQuery;
import io.github.perardua.staysupply.adapter.NightlyAvailability;
import io.github.perardua.staysupply.adapter.PropertyListing;
import io.github.perardua.staysupply.adapter.RoomTypeListing;
import io.github.perardua.staysupply.adapter.SupplierClient;
import io.github.perardua.staysupply.adapter.SupplierClientErrorException;
import io.github.perardua.staysupply.adapter.SupplierClientException;
import io.github.perardua.staysupply.adapter.SupplierMalformedException;
import io.github.perardua.staysupply.adapter.SupplierOffer;
import io.github.perardua.staysupply.adapter.SupplierPoolExhaustedException;
import io.github.perardua.staysupply.adapter.SupplierServerException;
import io.github.perardua.staysupply.adapter.SupplierTimeoutException;
import io.github.perardua.staysupply.supplier.Supplier;
import reactor.core.publisher.Mono;

@Component
public class SupplierAClient implements SupplierClient {

    private final WebClient webClient;
    private final Duration propertyListTimeout;
    private final Duration availabilityTimeout;

    public SupplierAClient(WebClient supplierAWebClient, SupplierAProperties properties) {
        this.webClient = supplierAWebClient;
        this.propertyListTimeout = Duration.ofMillis(properties.propertyListTimeoutMillis());
        this.availabilityTimeout = Duration.ofMillis(properties.availabilityTimeoutMillis());
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
                    .timeout(propertyListTimeout)
                    .block();
        } catch (SupplierClientException e) {
            throw e;
        } catch (Exception e) {
            // block()은 검사 예외를 ReactiveException으로 감싸므로 원인 사슬을 따라간다.
            // 풀 포화를 먼저 본다. 풀의 획득 타임아웃은 TimeoutException이라 순서를 바꾸면 가려진다.
            if (SupplierPoolExhaustedException.isPoolExhaustion(e)) {
                throw new SupplierPoolExhaustedException(Supplier.A,
                        "connection pool exhausted - hotels API call not attempted", e);
            }
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

    @Override
    public Mono<List<SupplierOffer>> fetchAvailability(List<String> supplierCodes, AvailabilityQuery query) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/a/v1/availability")
                        .queryParam("hotelCodes", String.join(",", supplierCodes))
                        .queryParam("checkIn", query.checkIn())
                        .queryParam("checkOut", query.checkOut())
                        .queryParam("adults", query.adults())
                        .queryParam("children", query.children())
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, r -> Mono.error(new SupplierClientErrorException(
                        Supplier.A, "availability API responded " + r.statusCode(), null)))
                .onStatus(HttpStatusCode::is5xxServerError, r -> Mono.error(new SupplierServerException(
                        Supplier.A, "availability API responded " + r.statusCode(), null)))
                .bodyToMono(AAvailabilityResponse.class)
                .timeout(availabilityTimeout)
                .map(response -> toOffers(response, query))
                .defaultIfEmpty(List.of())
                .onErrorMap(e -> !(e instanceof SupplierClientException), this::toSupplierException);
    }

    private SupplierClientException toSupplierException(Throwable e) {
        // 풀 포화를 먼저 본다. 풀의 획득 타임아웃은 TimeoutException이라 순서를 바꾸면 가려진다.
        if (SupplierPoolExhaustedException.isPoolExhaustion(e)) {
            return new SupplierPoolExhaustedException(Supplier.A,
                    "connection pool exhausted - availability API call not attempted", e);
        }
        if (hasCause(e, TimeoutException.class)) {
            return new SupplierTimeoutException(Supplier.A, "availability API timed out", e);
        }
        if (hasCause(e, DecodingException.class)) {
            return new SupplierMalformedException(Supplier.A, "availability API response could not be decoded", e);
        }
        return new SupplierServerException(Supplier.A, "availability API call failed", e);
    }

    private List<SupplierOffer> toOffers(AAvailabilityResponse response, AvailabilityQuery query) {
        if (response.items() == null) {
            return List.of();
        }
        return response.items().stream().map(item -> toOffer(item, query)).toList();
    }

    private SupplierOffer toOffer(AAvailability item, AvailabilityQuery query) {
        List<ADailyRate> dailyRates = item.dailyRates() == null ? List.of() : item.dailyRates();

        // 중복 날짜는 요금까지 이중으로 더해지므로 합산에 들어가기 전에 걸러낸다.
        Map<LocalDate, Integer> remainingByDate = remainingRoomsByDate(item, dailyRates);

        AFare fare = AFare.of(dailyRates, query);

        return new SupplierOffer(
                item.hotelCode(),
                item.hotelName(),
                item.roomTypeCode(),
                item.roomTypeName(),
                item.maxOccupancy(),
                NightlyAvailability.availableRooms(remainingByDate, query),
                item.breakfastIncluded(),
                item.currency(),
                fare.totalAmountIncludingTax(),
                fare.taxAmount());
    }

    private Map<LocalDate, Integer> remainingRoomsByDate(AAvailability item, List<ADailyRate> dailyRates) {
        Map<LocalDate, Integer> remainingByDate = new HashMap<>();
        for (ADailyRate rate : dailyRates) {
            if (rate.date() == null) {
                continue;
            }
            if (remainingByDate.put(rate.date(), rate.remainingRooms()) != null) {
                throw new SupplierMalformedException(Supplier.A,
                        "availability API returned duplicate date " + rate.date()
                                + " for " + item.hotelCode() + "/" + item.roomTypeCode(), null);
            }
        }
        return remainingByDate;
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

    record AAvailabilityResponse(List<AAvailability> items) {}

    record AAvailability(
            String hotelCode, String hotelName, String roomTypeCode, String roomTypeName,
            int maxOccupancy, boolean breakfastIncluded, String currency, List<ADailyRate> dailyRates) {}

    record ADailyRate(LocalDate date, int remainingRooms, long nightlyRate, long taxAmount) {}

    record AHotel(String hotelCode, String hotelName, List<ARoomType> roomTypes) {}

    record ARoomType(String roomTypeCode, String roomTypeName, int maxOccupancy) {}
}
