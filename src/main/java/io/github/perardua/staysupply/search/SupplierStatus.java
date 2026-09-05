package io.github.perardua.staysupply.search;

import io.github.perardua.staysupply.supplier.Supplier;

public record SupplierStatus(
        Supplier supplier,
        Status status,
        String message
) {
    public enum Status {
        OK, PARTIAL, TIMEOUT, SUPPLIER_ERROR, REQUEST_REJECTED, MALFORMED_RESPONSE, CIRCUIT_OPEN, POOL_EXHAUSTED
    }

    public static SupplierStatus ok(Supplier supplier) {
        return new SupplierStatus(supplier, Status.OK, null);
    }

    public static SupplierStatus failed(Supplier supplier, Status status, String message) {
        return new SupplierStatus(supplier, status, message);
    }
}
