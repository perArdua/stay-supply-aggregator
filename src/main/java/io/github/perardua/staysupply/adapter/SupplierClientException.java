package io.github.perardua.staysupply.adapter;

import io.github.perardua.staysupply.supplier.Supplier;

/**
 * 공급사 연동 실패의 공통 부모.
 *
 * <p>호출부가 실패 원인을 타입으로 구분할 수 있도록 sealed로 닫는다.
 * 공급사가 늘어도 실패의 종류는 여기 나열된 것을 벗어나지 않는다.
 */
public sealed abstract class SupplierClientException extends RuntimeException
        permits SupplierTimeoutException,
                SupplierServerException,
                SupplierClientErrorException,
                SupplierMalformedException,
                SupplierCircuitOpenException,
                SupplierPoolExhaustedException {

    private final Supplier supplier;

    public SupplierClientException(Supplier supplier, String message, Throwable cause) {
        super(message, cause);
        this.supplier = supplier;
    }

    public Supplier supplier() {
        return supplier;
    }
}
