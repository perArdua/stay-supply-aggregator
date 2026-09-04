package io.github.perardua.staysupply.search;

import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;

import io.github.perardua.staysupply.adapter.SupplierClient;

@Configuration
@Profile("!mock")
@EnableConfigurationProperties(SearchProperties.class)
public class SearchConfig {

    @Bean
    public SearchRepository searchRepository(JdbcClient jdbcClient) {
        return new SearchRepository(jdbcClient);
    }

    @Bean
    public SearchService searchService(List<SupplierClient> supplierClientList,
                                       SearchRepository searchRepository,
                                       SearchProperties searchProperties) {
        return new SearchService(supplierClientList, searchRepository, searchProperties);
    }
}
