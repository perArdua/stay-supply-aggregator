package io.github.perardua.staysupply.sync;

import org.springframework.scheduling.annotation.Scheduled;

public class SyncScheduler {

    private final SyncService syncService;

    public SyncScheduler(SyncService syncService) {
        this.syncService = syncService;
    }

    @Scheduled(cron = "${sync.cron}")
    public void run() {
        syncService.syncAll();
    }
}
