package io.github.perardua.staysupply.context;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.github.perardua.staysupply.supplier.Supplier;
import io.github.perardua.staysupply.sync.MappingWriter;
import io.github.perardua.staysupply.sync.SyncRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Tag("docker")
class MappingWriterTransactionTest {

    @Autowired
    MappingWriter mappingWriter;

    @MockitoBean
    SyncRepository syncRepository;

    @Test
    void 매핑_저장_빈은_AOP_프록시로_감싸여_있다() {
        assertThat(AopUtils.isAopProxy(mappingWriter)).isTrue();
    }

    @Test
    void 매핑_저장_메서드_안에서는_트랜잭션이_실제로_열려_있다() {
        AtomicBoolean transactionWasActive = new AtomicBoolean();
        given(syncRepository.deactivateStale(any(Supplier.class), any(LocalDateTime.class)))
                .willAnswer(invocation -> {
                    transactionWasActive.set(TransactionSynchronizationManager.isActualTransactionActive());
                    return 0;
                });

        mappingWriter.write(Supplier.A, List.of());

        assertThat(transactionWasActive).isTrue();
    }

}
