package io.github.perardua.staysupply.sync;

import io.github.perardua.staysupply.adapter.PropertyListing;
import io.github.perardua.staysupply.adapter.SupplierClient;

import java.util.List;

public class SupplierSyncExecutor {

    private final MappingWriter mappingWriter;

    public SupplierSyncExecutor(MappingWriter mappingWriter) {
        this.mappingWriter = mappingWriter;
    }

    public SyncResult sync(SupplierClient supplierClient) {
        // 공급사 호출은 트랜잭션 밖에서 한다.
        List<PropertyListing> propertyListingList = supplierClient.fetchProperties();

        // 목록을 받지 못한 채 저장 구간에 들어가면 스윕이 이 공급사의 매핑을 전부 비활성화한다.
        if (propertyListingList.isEmpty()) {
            throw new EmptyPropertyListException(supplierClient.supplier());
        }

        return mappingWriter.write(supplierClient.supplier(), propertyListingList);
    }
}
