package io.github.perardua.staysupply.adapter;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("!mock")
@Component
public class SupplierWarmupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SupplierWarmupRunner.class);

    private final List<SupplierClient> supplierClientList;

    public SupplierWarmupRunner(List<SupplierClient> supplierClientList) {
        this.supplierClientList = supplierClientList;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (SupplierClient client : supplierClientList) {
            warmUp(client);
        }
    }

    private void warmUp(SupplierClient client) {
        try {
            client.fetchProperties();
            log.info("supplier call path warmed up - supplier={}", client.supplier());
        } catch (Exception e) {
            // 경로를 밟는 것이 목적이라 실패도 정상 결과로 본다. 기동을 막지 않는다.
            log.info("supplier warm-up call failed - supplier={} reason={}", client.supplier(), e.toString());
        }
    }
}
