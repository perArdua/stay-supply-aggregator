package io.github.perardua.staysupply.sync;

import io.github.perardua.staysupply.adapter.SupplierClient;
import io.github.perardua.staysupply.adapter.SupplierClientException;

import java.util.ArrayList;
import java.util.List;

public class SyncService {

    private final List<SupplierClient> supplierClientList;
    private final SupplierSyncExecutor supplierSyncExecutor;
    private final SyncRepository syncRepository;

    public SyncService(List<SupplierClient> supplierClientList, SupplierSyncExecutor supplierSyncExecutor, SyncRepository syncRepository) {
        this.supplierClientList = supplierClientList;
        this.supplierSyncExecutor = supplierSyncExecutor;
        this.syncRepository = syncRepository;
    }

    public List<SyncResult> syncAll() {
        List<SyncResult> ret = new ArrayList<>();
        for (SupplierClient client : supplierClientList) {
            ret.add(syncOne(client));
        }
        return ret;
    }

    public boolean hasAnyMapping() {
        return syncRepository.hasAnyMapping();
    }

    public SyncResult syncOne(SupplierClient client) {
        try {
            return supplierSyncExecutor.sync(client);
        } catch (EmptyPropertyListException e) {
            return SyncResult.failed(client.supplier(), SyncResult.Status.EMPTY_LIST, e.getMessage());
        } catch (SupplierClientException e) {
            return SyncResult.failed(client.supplier(), SyncResult.Status.FAILED, e.getMessage());
        } catch (Exception e) {
            return SyncResult.failed(client.supplier(), SyncResult.Status.FAILED_UNKNOWN, e.getMessage());
        }
    }
}
