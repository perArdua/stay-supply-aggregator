package io.github.perardua.staysupply.adapter;

import io.github.perardua.staysupply.search.SearchQuery;
import io.github.perardua.staysupply.supplier.Supplier;

import java.util.List;

import reactor.core.publisher.Mono;

public interface SupplierClient {

    Supplier supplier();

    List<PropertyListing> fetchProperties();

    Mono<List<SupplierOffer>> fetchAvailability(List<String> supplierCodes, SearchQuery query);
}
