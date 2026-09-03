package io.github.perardua.staysupply.sync;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;

import io.github.perardua.staysupply.adapter.SupplierClient;

/**
 * 동기화 기능 전체를 하나의 조건 아래 묶는다.
 *
 * <p>mock 프로필은 공급사 스텁만 띄우는 별도 프로세스라 DataSource가 없다.
 * 빈 등록을 여기서 막지 않으면 SyncRepository가 JdbcClient를 찾다가 기동이 실패한다.
 *
 * <p>스케줄링이 필요한 이유도 동기화뿐이므로 {@code @EnableScheduling}을 같은 조건에 둔다.
 * mock 프로필에서는 스케줄링 인프라 자체가 뜨지 않는다.
 */
@Configuration
@Profile("!mock")
@EnableScheduling
public class SyncConfig {

    @Bean
    public SyncRepository syncRepository(JdbcClient jdbcClient) {
        return new SyncRepository(jdbcClient);
    }

    @Bean
    public SupplierSyncExecutor supplierSyncExecutor(SyncRepository syncRepository) {
        return new SupplierSyncExecutor(syncRepository);
    }

    @Bean
    public SyncService syncService(List<SupplierClient> supplierClientList,
                                   SupplierSyncExecutor supplierSyncExecutor,
                                   SyncRepository syncRepository) {
        return new SyncService(supplierClientList, supplierSyncExecutor, syncRepository);
    }

    @Bean
    public SyncScheduler syncScheduler(SyncService syncService) {
        return new SyncScheduler(syncService);
    }

    @Bean
    public SyncStartupRunner syncStartupRunner(SyncService syncService) {
        return new SyncStartupRunner(syncService);
    }
}
