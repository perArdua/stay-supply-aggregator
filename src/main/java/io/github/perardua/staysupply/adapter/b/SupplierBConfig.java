package io.github.perardua.staysupply.adapter.b;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.reactive.ClientHttpConnectorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(SupplierBProperties.class)
public class SupplierBConfig {

    private static final String API_KEY_HEADER = "X-Api-Key";

    @Bean
    public WebClient supplierBWebClient(WebClient.Builder builder, SupplierBProperties properties) {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(Duration.ofMillis(properties.connectTimeoutMillis()));

        ClientHttpConnector connector = ClientHttpConnectorBuilder.reactor().build(settings);

        return builder
                .baseUrl(properties.baseUrl())
                .defaultHeader(API_KEY_HEADER, properties.apiKey())
                .clientConnector(connector)
                .build();
    }
}
