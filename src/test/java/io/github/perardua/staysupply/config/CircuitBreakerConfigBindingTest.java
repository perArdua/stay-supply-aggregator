package io.github.perardua.staysupply.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;

import io.github.perardua.staysupply.adapter.SupplierPoolExhaustedException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 서킷 설정은 default 하나만 정의하고 인스턴스는 공급사 이름으로 실행 중에 만들어진다.
 * 키가 어긋나면 라이브러리 기본값이 조용히 쓰이고, 실패 임계나 창 크기가 달라져도 응답은 그대로 나온다.
 *
 */
class CircuitBreakerConfigBindingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(CircuitBreakerAutoConfiguration.class));

    /** 실행 중 만들어지는 인스턴스가 default 설정을 물려받는지까지 함께 본다. */
    private void withSupplierCircuitConfig(java.util.function.Consumer<CircuitBreakerConfig> assertion) {
        runner.run(context -> assertion.accept(context.getBean(CircuitBreakerRegistry.class)
                .circuitBreaker("A-availability")
                .getCircuitBreakerConfig()));
    }

    @Test
    void 서킷의_슬라이딩_윈도는_호출_10건이다() {
        withSupplierCircuitConfig(config -> {
            assertThat(config.getSlidingWindowType()).isEqualTo(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED);
            assertThat(config.getSlidingWindowSize()).isEqualTo(10);
        });
    }

    @Test
    void 서킷은_호출_10건이_모이기_전에는_판정하지_않는다() {
        withSupplierCircuitConfig(config -> assertThat(config.getMinimumNumberOfCalls()).isEqualTo(10));
    }

    @Test
    void 서킷은_실패율_50퍼센트에서_열린다() {
        withSupplierCircuitConfig(config -> assertThat(config.getFailureRateThreshold()).isEqualTo(50f));
    }

    @Test
    void 반열림_상태에서_허용하는_프로브는_1건이다() {
        // 회복 중인 공급사가 검색 하나의 청크를 한꺼번에 받지 않도록 정원을 1로 둔다.
        withSupplierCircuitConfig(config ->
                assertThat(config.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(1));
    }

    @Test
    void 커넥션_풀_포화는_서킷의_실패_집계에서_빠진다() {
        // 우리 쪽 자원 부족이라 세면 부하가 서킷을 열고 부하가 걷힌 뒤에도 공급사를 더 막는다.
        withSupplierCircuitConfig(config -> assertThat(
                config.getIgnoreExceptionPredicate().test(
                        new SupplierPoolExhaustedException(null, "pool", null))).isTrue());
    }
}
