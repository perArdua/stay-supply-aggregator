package io.github.perardua.staysupply.adapter.b;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "suppliers.b")
public record SupplierBProperties(
        String baseUrl,
        String apiKey,
        int connectTimeoutMillis,
        int propertyListTimeoutMillis,
        int availabilityTimeoutMillis,
        int maxConnections
) {
}
