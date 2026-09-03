package io.github.perardua.staysupply.sync;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 관리자용 수동 재동기화 호출 API
 * 만약 운영환경에서 사용한다면 관리자로 인증된 사용자만
 * 사용할 수 있도록 별도의 조치를 취해야 함
 */

@Profile("!mock")
@RestController
@RequestMapping("/internal")
public class SyncController {

    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/sync")
    public List<SyncResult> sync() {
        return syncService.syncAll();
    }
}
