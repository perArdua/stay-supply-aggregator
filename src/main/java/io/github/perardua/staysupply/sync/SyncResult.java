package io.github.perardua.staysupply.sync;

import io.github.perardua.staysupply.supplier.Supplier;

public record SyncResult(
        Supplier supplier,
        Status status,
        int propertyCount,
        int roomTypeCount,
        String message
) {
    public enum Status { OK, EMPTY_LIST, FAILED, FAILED_UNKNOWN }

    public static SyncResult ok(Supplier supplier, int properties, int roomTypes) {
        return new SyncResult(supplier, Status.OK, properties, roomTypes, null);
    }

    public static SyncResult failed(Supplier supplier, Status status, String message) {
        return new SyncResult(supplier, status, 0, 0, message);
    }
}