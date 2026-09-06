package io.github.perardua.staysupply.context;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import io.github.perardua.staysupply.adapter.SupplierClient;
import io.github.perardua.staysupply.adapter.SupplierWarmupRunner;
import io.github.perardua.staysupply.mock.MockSupplierController;
import io.github.perardua.staysupply.supplier.Supplier;
import io.github.perardua.staysupply.sync.MappingWriter;
import io.github.perardua.staysupply.sync.SyncController;
import io.github.perardua.staysupply.sync.SyncScheduler;
import io.github.perardua.staysupply.sync.SyncService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 기본 프로필에서 검색과 동기화가 실제로 조립되는지 본다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Tag("docker")
class DefaultProfileContextTest {

    @Autowired
    ApplicationContext context;

    @Test
    void 기본_프로필에는_동기화_컨트롤러와_서비스가_있다() {
        assertThat(context.getBeansOfType(SyncController.class)).hasSize(1);
        assertThat(context.getBeansOfType(SyncService.class)).hasSize(1);
    }

    @Test
    void 기본_프로필에는_매핑_저장_빈과_스케줄러가_있다() {
        assertThat(context.getBeansOfType(MappingWriter.class)).hasSize(1);
        assertThat(context.getBeansOfType(SyncScheduler.class)).hasSize(1);
    }

    @Test
    void 기본_프로필에는_공급사_스텁_컨트롤러가_없다() {
        assertThat(context.getBeansOfType(MockSupplierController.class)).isEmpty();
    }

    @Test
    void 두_공급사의_클라이언트가_모두_등록된다() {
        // 하나가 빠지면 그 공급사는 검색에서 조용히 사라지고 응답은 정상으로 보인다.
        assertThat(context.getBeansOfType(SupplierClient.class).values())
                .extracting(SupplierClient::supplier)
                .containsExactlyInAnyOrder(Supplier.A, Supplier.B);
    }

    @Test
    void 공급사_웜업_러너가_등록된다() {
        assertThat(context.getBeansOfType(SupplierWarmupRunner.class)).hasSize(1);
    }
}
