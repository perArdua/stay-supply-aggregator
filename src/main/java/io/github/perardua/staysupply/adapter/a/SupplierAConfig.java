package io.github.perardua.staysupply.adapter.a;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.reactive.ClientHttpConnectorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

@Configuration
@EnableConfigurationProperties(SupplierAProperties.class)
public class SupplierAConfig {

    private static final String API_KEY_HEADER = "X-Api-Key";

    // 공급사마다 풀을 나눈다. 공유하면 한 공급사가 커넥션을 붙잡고 있을 때
    // 다른 공급사의 획득까지 밀려 공급사 단위로 나눠 둔 타임아웃과 서킷의 격리가 깨진다.
    @Bean(destroyMethod = "dispose")
    public ConnectionProvider supplierAConnectionProvider(SupplierAProperties properties) {
        return ConnectionProvider.builder("supplier-a")
                .maxConnections(properties.maxConnections())
                .build();
    }

    @Bean
    public WebClient supplierAWebClient(WebClient.Builder builder,
                                        SupplierAProperties properties,
                                        ConnectionProvider supplierAConnectionProvider) {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(Duration.ofMillis(properties.connectTimeoutMillis()));

        // 기본 팩터리는 HttpClient.create()라 전역 HttpResources 풀을 쓴다. 우리 풀로 바꾼다.
        ClientHttpConnector connector = ClientHttpConnectorBuilder.reactor()
                .withHttpClientFactory(() -> HttpClient.create(supplierAConnectionProvider))
                .build(settings);

        return builder
                .baseUrl(properties.baseUrl())
                .defaultHeader(API_KEY_HEADER, properties.apiKey())
                .clientConnector(connector)
                .build();
    }
}
