package io.github.perardua.staysupply.sync;

import io.github.perardua.staysupply.adapter.PropertyListing;
import io.github.perardua.staysupply.adapter.RoomTypeListing;
import io.github.perardua.staysupply.adapter.SupplierClient;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public class SupplierSyncExecutor {

    private final SyncRepository syncRepository;

    public SupplierSyncExecutor(SyncRepository syncRepository) {
        this.syncRepository = syncRepository;
    }

    @Transactional
    public SyncResult sync(SupplierClient supplierClient) {
        LocalDateTime startedAt = LocalDateTime.now();

        List<PropertyListing> propertyListingList = supplierClient.fetchProperties();

        if (propertyListingList.isEmpty()) {
            throw new EmptyPropertyListException(supplierClient.supplier());
        }

        int roomTypeCount = 0;

        for (PropertyListing property: propertyListingList) {
            long propertyId = syncRepository.upsertProperty(
                    supplierClient.supplier(), property.supplierCode(), startedAt
            );

            for (RoomTypeListing roomType : property.roomTypeListingList()) {
                syncRepository.upsertRoomType(propertyId, roomType.supplierCode(), startedAt);
                ++roomTypeCount;
            }
        }

        syncRepository.deactivateStale(supplierClient.supplier(), startedAt);

        return SyncResult.ok(supplierClient.supplier(), propertyListingList.size(), roomTypeCount);
    }
}
