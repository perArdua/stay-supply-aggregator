package io.github.perardua.staysupply.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * 서버 시작 시 핑 테이블이 비어 있는 경우에만 실행됨
 */
public class SyncStartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SyncStartupRunner.class);

    private final SyncService syncService;

    public SyncStartupRunner(SyncService syncService) {
        this.syncService = syncService;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (syncService.hasAnyMapping()) {
            log.info("startup sync skipped - mapping table is not empty");
            return;
        }

        log.info("mapping table is empty - running startup sync");
        syncService.syncAll();
    }
}
