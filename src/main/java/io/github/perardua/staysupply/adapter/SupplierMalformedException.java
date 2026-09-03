package io.github.perardua.staysupply.adapter;

import io.github.perardua.staysupply.supplier.Supplier;

/**
 * 응답을 해석할 수 없는 경우.
 *
 * <p>역직렬화 실패, 미지의 resultCode, 성공인데 data가 비어 있는 응답을 포함한다.
 * 셋 다 "공급사가 스펙과 다르게 답했다"는 같은 사실을 가리킨다.
 */
public final class SupplierMalformedException extends SupplierClientException {

    public SupplierMalformedException(Supplier supplier, String message, Throwable cause) {
        super(supplier, message, cause);
    }
}
