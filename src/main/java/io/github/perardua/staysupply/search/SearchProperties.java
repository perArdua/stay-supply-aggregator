package io.github.perardua.staysupply.search;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "search")
public record SearchProperties(
        int maxConcurrentCallsPerSupplier
) {
}
