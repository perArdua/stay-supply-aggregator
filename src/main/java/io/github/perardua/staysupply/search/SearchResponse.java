package io.github.perardua.staysupply.search;

import java.util.List;

public record SearchResponse(
        List<StayOffer> offers,
        List<SupplierStatus> suppliers
) {
}
