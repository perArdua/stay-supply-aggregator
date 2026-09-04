package io.github.perardua.staysupply.search;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchErrorResponse(
        String code,
        String message,
        List<SupplierStatus> suppliers
) {
    public static SearchErrorResponse invalidRequest(String message) {
        return new SearchErrorResponse("INVALID_REQUEST", message, null);
    }

    // offers는 담지 않는다. 빈 배열을 주면 "결과 없음"과 구분되지 않아 503으로 나눈 뜻이 사라진다.
    public static SearchErrorResponse allSuppliersFailed(List<SupplierStatus> suppliers) {
        return new SearchErrorResponse(
                "ALL_SUPPLIERS_FAILED", "every supplier failed; see suppliers for details", suppliers);
    }
}
