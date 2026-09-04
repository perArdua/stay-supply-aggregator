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

@Profile("!mock")
@RestController
@RequestMapping("/api/v1/stays")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam int adults,
            @RequestParam int children) {

        AvailabilityQuery query = new AvailabilityQuery(checkIn, checkOut, adults, children);

        // 서블릿 워커 스레드라 block()이 이벤트 루프를 막지는 않는다.
        SearchResponse response = searchService.search(query).block();

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
