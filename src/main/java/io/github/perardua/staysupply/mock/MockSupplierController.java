package io.github.perardua.staysupply.mock;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 요청 파라미터는 무시하고 모드에 따라 고정 응답 반환
 * <p>실행: {@code ./gradlew bootRun --args='--spring.profiles.active=mock'} (9090)
 *
 * <p>모드 전환 (공급사별 독립, 기본 normal):
 * <pre>
 * curl -X POST 'http://localhost:9090/control/a/mode?value=no-response'
 * curl -X POST 'http://localhost:9091/control/b/mode?value=error'
 * curl -X POST 'http://localhost:9090/control/a/mode?value=normal'
 * </pre>
 */
@Profile("mock")
@RestController
public class MockSupplierController {

    private static final String SUPPLIER_A = "a";
    private static final String SUPPLIER_B = "b";

    private static final String NORMAL = "normal";
    private static final String ERROR = "error";
    private static final String NO_RESPONSE = "no-response";
    private static final String DELAY = "delay";
    private static final String EMPTY = "empty";

    private static final long NO_RESPONSE_MILLIS = 30_000L;
    private static final long DELAY_MILLIS = 5_000L;

    private final Map<String, String> modes = new ConcurrentHashMap<>();

    // ── 모드 제어 ────────────────────────────────────────────────
    @PostMapping("/control/{supplier}/mode")
    public Map<String, String> setMode(@PathVariable String supplier, @RequestParam String value) {
        modes.put(supplier, value);
        return Map.of(supplier, value);
    }

    // ── ① 숙소 목록 (정적 콘텐츠) ─────────────────────────────────
    @GetMapping(value = "/a/v1/hotels", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> hotelsA() {
        if (EMPTY.equals(modes.get(SUPPLIER_A))) {
            return ResponseEntity.ok("{\"items\":[]}");
        }
        return respond(SUPPLIER_A, A_HOTELS);
    }

    @GetMapping(value = "/b/api/properties", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> propertiesB() {
        return respond(SUPPLIER_B, B_PROPERTIES);
    }

    // ── ② 재고·요금 조회 ──────────────────────────────────────────
    @GetMapping(value = "/a/v1/availability", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> availabilityA(
            @RequestParam(required = false) String hotelCodes,
            @RequestParam(required = false) String checkIn,
            @RequestParam(required = false) String checkOut,
            @RequestParam(required = false) Integer adults,
            @RequestParam(required = false) Integer children) {
        return respond(SUPPLIER_A, A_AVAILABILITY);
    }

    @GetMapping(value = "/b/api/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> searchB(
            @RequestParam(required = false) String propertyIds,
            @RequestParam(required = false) String checkIn,
            @RequestParam(required = false) String checkOut,
            @RequestParam(required = false) Integer adults,
            @RequestParam(required = false) Integer children) {
        return respond(SUPPLIER_B, B_SEARCH);
    }

    // ── 모드별 응답 ──────────────────────────────────────────────
    private ResponseEntity<String> respond(String supplier, String successBody) {
        String mode = modes.getOrDefault(supplier, NORMAL);
        if (ERROR.equals(mode)) {
            return errorResponse(supplier);
        }
        if (NO_RESPONSE.equals(mode)) {
            // 끝까지 자지 못했다면 무응답이 깨진 것이므로 성공 응답을 내지 않는다.
            if (!sleep(NO_RESPONSE_MILLIS)) {
                return ResponseEntity.status(503).build();
            }
        } else if (DELAY.equals(mode)) {
            sleep(DELAY_MILLIS);
        }
        return ResponseEntity.ok(successBody);
    }

    private ResponseEntity<String> errorResponse(String supplier) {
        if (SUPPLIER_B.equals(supplier)) {
            // B는 장애 상황에서도 HTTP 200이다. 실패는 본문 resultCode로만 알린다.
            return ResponseEntity.ok(B_ERROR);
        }
        return ResponseEntity.status(503).body(A_ERROR);
    }

    /** 끝까지 잤으면 true, 인터럽트로 중간에 깨어났으면 false. */
    private boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    // ── 응답 본문 ────────────────────────────────────────────────
    private static final String A_HOTELS = """
            {
              "items": [
                {
                  "hotelCode": "A-10023",
                  "hotelName": "Riverside Hotel Seoul",
                  "roomTypes": [
                    { "roomTypeCode": "DLX-TWN", "roomTypeName": "Deluxe Twin", "maxOccupancy": 2 }
                  ]
                },
                {
                  "hotelCode": "A-10044",
                  "hotelName": "Namsan Garden Stay",
                  "roomTypes": [
                    { "roomTypeCode": "STD-DBL", "roomTypeName": "Standard Double", "maxOccupancy": 2 }
                  ]
                }
              ]
            }
            """;

    private static final String A_AVAILABILITY = """
            {
              "items": [
                {
                  "hotelCode": "A-10023",
                  "hotelName": "Riverside Hotel Seoul",
                  "roomTypeCode": "DLX-TWN",
                  "roomTypeName": "Deluxe Twin",
                  "maxOccupancy": 2,
                  "breakfastIncluded": false,
                  "currency": "KRW",
                  "dailyRates": [
                    { "date": "2026-09-01", "remainingRooms": 3, "nightlyRate": 120000, "taxAmount": 12000 },
                    { "date": "2026-09-02", "remainingRooms": 1, "nightlyRate": 150000, "taxAmount": 15000 },
                    { "date": "2026-09-03", "remainingRooms": 5, "nightlyRate": 120000, "taxAmount": 12000 }
                  ]
                },
                {
                  "hotelCode": "A-10044",
                  "hotelName": "Namsan Garden Stay",
                  "roomTypeCode": "STD-DBL",
                  "roomTypeName": "Standard Double",
                  "maxOccupancy": 2,
                  "breakfastIncluded": false,
                  "currency": "KRW",
                  "dailyRates": [
                    { "date": "2026-09-01", "remainingRooms": 2, "nightlyRate": 88000, "taxAmount": 8800 },
                    { "date": "2026-09-02", "remainingRooms": 0, "nightlyRate": 99000, "taxAmount": 9900 },
                    { "date": "2026-09-03", "remainingRooms": 4, "nightlyRate": 88000, "taxAmount": 8800 }
                  ]
                }
              ]
            }
            """;

    private static final String B_PROPERTIES = """
            {
              "resultCode": "0000",
              "resultMessage": "SUCCESS",
              "data": {
                "items": [
                  {
                    "propertyId": "B77120",
                    "propertyName": "Riverside Hotel Seoul",
                    "rooms": [
                      { "roomId": "R-401", "roomName": "Deluxe Twin Room", "maxOccupancy": 2 }
                    ]
                  }
                ]
              }
            }
            """;

    private static final String B_SEARCH = """
            {
              "resultCode": "0000",
              "resultMessage": "SUCCESS",
              "data": {
                "items": [
                  {
                    "propertyId": "B77120",
                    "propertyName": "Riverside Hotel Seoul",
                    "roomId": "R-401",
                    "roomName": "Deluxe Twin Room",
                    "maxOccupancy": 2,
                    "breakfastIncluded": true,
                    "currency": "KRW",
                    "totalPrice": 452000,
                    "taxIncluded": true,
                    "inventory": [
                      { "date": "2026-09-01", "remainingRooms": 3 },
                      { "date": "2026-09-02", "remainingRooms": 1 },
                      { "date": "2026-09-03", "remainingRooms": 5 }
                    ]
                  }
                ]
              }
            }
            """;

    private static final String A_ERROR = """
            {"error":"SERVICE_UNAVAILABLE","message":"temporarily unavailable"}
            """;

    private static final String B_ERROR = """
            {"resultCode":"E503","resultMessage":"TEMPORARILY_UNAVAILABLE","data":null}
            """;
}
