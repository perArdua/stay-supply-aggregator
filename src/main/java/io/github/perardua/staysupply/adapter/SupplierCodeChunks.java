package io.github.perardua.staysupply.adapter;

import java.util.ArrayList;
import java.util.List;

/**
 * 공급사 재고 요금 API가 한 번에 받는 숙소 코드 수의 상한. 넘기면 공급사가 요청을 거부한다.
 * 이 값은 호출하는 쪽의 사정이 아니라 공급사 API의 계약이므로 어댑터 경계에 둔다.
 */
public final class SupplierCodeChunks {

    private static final int MAX_CODES_PER_CALL = 50;

    private SupplierCodeChunks() {
    }

    public static List<List<String>> chunk(List<String> codes) {
        List<List<String>> chunks = new ArrayList<>();
        for (int from = 0; from < codes.size(); from += MAX_CODES_PER_CALL) {
            chunks.add(codes.subList(from, Math.min(from + MAX_CODES_PER_CALL, codes.size())));
        }
        return chunks;
    }
}
