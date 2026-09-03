package io.github.perardua.staysupply.adapter.a;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "suppliers.a")
public record SupplierAProperties(
        String baseUrl,
        String apiKey,
        int connectTimeoutMillis,
        int overallTimeoutMillis
) {
}
