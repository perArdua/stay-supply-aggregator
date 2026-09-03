package io.github.perardua.staysupply.adapter;

import io.github.perardua.staysupply.supplier.Supplier;

import java.util.List;

public interface SupplierClient {

    Supplier supplier();

    List<PropertyListing> fetchProperties();
}
