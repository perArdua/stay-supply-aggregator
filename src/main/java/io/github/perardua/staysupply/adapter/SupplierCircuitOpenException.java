package io.github.perardua.staysupply.adapter;

import io.github.perardua.staysupply.supplier.Supplier;

/**
 * 서킷이 열려 호출 자체를 하지 않은 경우.
 *
 * <p>공급사가 답한 결과가 아니라 직전 실패들을 근거로 우리가 호출을 건너뛴 것이다.
 */
public final class SupplierCircuitOpenException extends SupplierClientException {

    public SupplierCircuitOpenException(Supplier supplier, String message, Throwable cause) {
        super(supplier, message, cause);
    }
}
