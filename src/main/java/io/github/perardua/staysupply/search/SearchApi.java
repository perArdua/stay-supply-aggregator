package io.github.perardua.staysupply.search;

import java.time.LocalDate;

import org.springframework.http.ResponseEntity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Mono;

@Tag(name = "숙박 상품 검색", description = "여러 공급사의 재고·요금을 병렬 조회해 하나의 표준 모델로 병합한다")
public interface SearchApi {

    @Operation(
            summary = "통합 숙박 상품 검색",
            description = """
                    날짜와 인원으로 자사 보유 숙소 전체를 조회한다. 지역·키워드 필터는 없다.
                    체크아웃일은 숙박일에 포함되지 않는다(9/1 체크인, 9/4 체크아웃 = 3박).

                    `availableRooms`는 요청 기간 전체를 예약할 수 있는 객실 수이며 기간 중 최솟값이다.
                    0이면 예약할 수 없지만 응답에서 빼지 않는다. 없는 숙소와 그날 방이 없는 숙소를 구분하기 위함이다.

                    일부 공급사만 실패해도 나머지 결과로 200을 반환하고, 실패 사실은 `suppliers`에 남는다.""")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "검색 성공. 일부 공급사가 실패한 부분 실패도 여기에 포함되며 `suppliers`로 드러난다",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SearchResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청이 잘못됨. 체크아웃이 체크인보다 앞서거나 인원이 음수인 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SearchErrorResponse.class))),
            @ApiResponse(
                    responseCode = "503",
                    description = "모든 공급사가 실패해 내보낼 상품이 하나도 없음. `offers`를 빈 배열로 주지 않고 상태로 나눈다",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SearchErrorResponse.class)))
    })
    Mono<ResponseEntity<?>> search(
            @Parameter(description = "체크인일", example = "2026-09-01") LocalDate checkIn,
            @Parameter(description = "체크아웃일. 이 날은 숙박일에 포함되지 않는다", example = "2026-09-04") LocalDate checkOut,
            @Parameter(description = "성인 수", example = "2") int adults,
            @Parameter(description = "아동 수. `maxOccupancy`는 성인과 아동의 합으로 판정된다", example = "0") int children);
}
