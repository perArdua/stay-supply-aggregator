package io.github.perardua.staysupply.sync;

import io.github.perardua.staysupply.supplier.Supplier;

public class EmptyPropertyListException extends RuntimeException {

    public EmptyPropertyListException(Supplier supplier) {
        super("supplier %s returned empty property list".formatted(supplier));
    }
}
