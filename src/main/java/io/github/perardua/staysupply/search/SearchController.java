package io.github.perardua.staysupply.search;

import java.time.LocalDate;

import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.perardua.staysupply.adapter.AvailabilityQuery;
import reactor.core.publisher.Mono;

@Profile("!mock")
@RestController
@RequestMapping("/api/v1/stays")
public class SearchController implements SearchApi {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @Override
    @GetMapping("/search")
    public Mono<ResponseEntity<?>> search(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam int adults,
            @RequestParam int children) {

        AvailabilityQuery query = new AvailabilityQuery(checkIn, checkOut, adults, children);

        // Mono를 그대로 돌려주어 워커 스레드가 응답까지 점유되지 않게 한다.
        return searchService.search(query).map(SearchController::toResponseEntity);
    }

    private static ResponseEntity<?> toResponseEntity(SearchResponse response) {
        if (response.allSuppliersFailed()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(SearchErrorResponse.allSuppliersFailed(response.suppliers()));
        }
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<SearchErrorResponse> handleInvalidRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(SearchErrorResponse.invalidRequest(e.getMessage()));
    }
}
