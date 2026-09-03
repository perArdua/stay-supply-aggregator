package io.github.perardua.staysupply.adapter.a;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.reactive.ClientHttpConnectorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(SupplierAProperties.class)
public class SupplierAConfig {

    private static final String API_KEY_HEADER = "X-Api-Key";

    @Bean
    public WebClient supplierAWebClient(WebClient.Builder builder, SupplierAProperties properties) {
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
