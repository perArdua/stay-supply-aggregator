package io.github.perardua.staysupply.adapter;

import io.github.perardua.staysupply.supplier.Supplier;

/** 공급사 측 오류. A는 HTTP 5xx, B는 resultCode E500 / E503. */
public final class SupplierServerException extends SupplierClientException {

    public SupplierServerException(Supplier supplier, String message, Throwable cause) {
        super(supplier, message, cause);
    }
}
