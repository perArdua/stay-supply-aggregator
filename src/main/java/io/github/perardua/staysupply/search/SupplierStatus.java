package io.github.perardua.staysupply.search;

import io.github.perardua.staysupply.supplier.Supplier;

public record SupplierStatus(
        Supplier supplier,
        Status status,
        String message
) {
    public enum Status { OK, PARTIAL, TIMEOUT, SUPPLIER_ERROR }

    public static SupplierStatus ok(Supplier supplier) {
        return new SupplierStatus(supplier, Status.OK, null);
    }
}
