package io.github.perardua.staysupply.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import io.github.perardua.staysupply.adapter.a.SupplierAProperties;
import io.github.perardua.staysupply.adapter.b.SupplierBProperties;
import io.github.perardua.staysupply.search.SearchProperties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * application.yaml의 값이 실제로 설정 객체에 실리는지 본다.
 */
class SupplierPropertiesBindingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(BindOnly.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({
            SupplierAProperties.class, SupplierBProperties.class, SearchProperties.class})
    static class BindOnly {
    }

    @Test
    void 공급사_A의_base_url은_9090이다() {
        runner.run(context -> assertThat(context.getBean(SupplierAProperties.class).baseUrl())
                .isEqualTo("http://localhost:9090"));
    }

    @Test
    void 공급사_B의_base_url은_9091로_A와_갈라져_있다() {
        // 두 공급사가 같은 주소를 보면 격리가 무너지고 한쪽 장애가 양쪽으로 번진다.
        runner.run(context -> assertThat(context.getBean(SupplierBProperties.class).baseUrl())
                .isEqualTo("http://localhost:9091"));
    }

    @Test
    void 두_공급사의_연결_타임아웃은_1000밀리초다() {
        runner.run(context -> assertThat(context.getBean(SupplierAProperties.class).connectTimeoutMillis())
                .isEqualTo(1000)
                .isEqualTo(context.getBean(SupplierBProperties.class).connectTimeoutMillis()));
    }

    @Test
    void 두_공급사의_숙소_목록_타임아웃은_10000밀리초다() {
        // 하루 한 번 배치로 부르고 고객이 기다리지 않으므로 크게 잡는다.
        runner.run(context -> assertThat(context.getBean(SupplierAProperties.class).propertyListTimeoutMillis())
                .isEqualTo(10000)
                .isEqualTo(context.getBean(SupplierBProperties.class).propertyListTimeoutMillis()));
    }

    @Test
    void 두_공급사의_재고_조회_타임아웃은_500밀리초다() {
        // 검색마다 부르고 고객 대기 시간에 그대로 묶인다.
        runner.run(context -> assertThat(context.getBean(SupplierAProperties.class).availabilityTimeoutMillis())
                .isEqualTo(500)
                .isEqualTo(context.getBean(SupplierBProperties.class).availabilityTimeoutMillis()));
    }

    @Test
    void 두_공급사의_커넥션_풀_상한은_100이다() {
        runner.run(context -> assertThat(context.getBean(SupplierAProperties.class).maxConnections())
                .isEqualTo(100)
                .isEqualTo(context.getBean(SupplierBProperties.class).maxConnections()));
    }

    @Test
    void 공급사당_동시_호출_상한은_10이다() {
        runner.run(context -> assertThat(
                context.getBean(SearchProperties.class).maxConcurrentCallsPerSupplier()).isEqualTo(10));
    }

    @Test
    void 검색_데드라인은_2000밀리초다() {
        runner.run(context -> assertThat(context.getBean(SearchProperties.class).deadlineMillis())
                .isEqualTo(2000L));
    }
}
