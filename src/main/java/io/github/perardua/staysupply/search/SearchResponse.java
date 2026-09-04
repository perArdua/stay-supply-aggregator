package io.github.perardua.staysupply.search;

import java.util.List;

public record SearchResponse(
        List<StayOffer> offers,
        List<SupplierStatus> suppliers
) {
    public boolean allSuppliersFailed() {
        return !suppliers.isEmpty() && suppliers.stream()
                .noneMatch(s -> s.status() == SupplierStatus.Status.OK
                        || s.status() == SupplierStatus.Status.PARTIAL);
    }
}
