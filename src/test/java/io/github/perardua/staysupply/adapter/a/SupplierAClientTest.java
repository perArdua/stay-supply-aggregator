package io.github.perardua.staysupply.adapter.a;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import io.github.perardua.staysupply.adapter.SupplierServerException;
import io.github.perardua.staysupply.adapter.SupplierTimeoutException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class SupplierAClientTest {
    @Autowired SupplierAClient client;

    @Test
    void fetchProperties() {
        client.fetchProperties().forEach(System.out::println);
    }

    @Test
    void 장애_모드에서_예외로_변환된다() {
        // 사전에: curl -X POST 'localhost:9090/control/a/mode?value=error'
        assertThatThrownBy(() -> client.fetchProperties())
                .isInstanceOf(SupplierServerException.class)
                .hasMessageContaining("503");
    }

    @Test
    void 무응답이면_전체_타임아웃에_걸린다() {
        // 사전에: curl -X POST 'localhost:9090/control/a/mode?value=no-response'
        assertThatThrownBy(() -> client.fetchProperties())
                .isInstanceOf(SupplierTimeoutException.class)
                .hasRootCauseInstanceOf(java.util.concurrent.TimeoutException.class);
    }
}
