package io.github.perardua.staysupply.adapter;

import io.github.perardua.staysupply.supplier.Supplier;

/** 요청 측 오류. A는 HTTP 4xx, B는 resultCode E400 / E401 / E429. */
public final class SupplierClientErrorException extends SupplierClientException {

    public SupplierClientErrorException(Supplier supplier, String message, Throwable cause) {
        super(supplier, message, cause);
    }
}
