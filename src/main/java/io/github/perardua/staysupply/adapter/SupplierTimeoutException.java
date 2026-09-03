package io.github.perardua.staysupply.adapter;

import io.github.perardua.staysupply.supplier.Supplier;

/** 응답이 전체 예산 안에 오지 않은 경우. */
public final class SupplierTimeoutException extends SupplierClientException {

    public SupplierTimeoutException(Supplier supplier, String message, Throwable cause) {
        super(supplier, message, cause);
    }
}
