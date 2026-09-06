package io.github.perardua.staysupply.sync;

import io.github.perardua.staysupply.adapter.PropertyListing;
import io.github.perardua.staysupply.adapter.RoomTypeListing;
import io.github.perardua.staysupply.supplier.Supplier;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 매핑 저장 구간. upsert 전체와 비활성화 스윕을 한 트랜잭션으로 묶는다.
 *
 * <p>공급사 호출을 이 안에 두지 않는다. 응답을 기다리는 동안 커넥션과 잠금을 붙잡게 되기 때문이다.
 * 같은 클래스 안에서 부르면 프록시를 타지 않으므로 호출부와 클래스를 나눠 둔다.
 */
public class MappingWriter {

    private final SyncRepository syncRepository;

    public MappingWriter(SyncRepository syncRepository) {
        this.syncRepository = syncRepository;
    }

    @Transactional
    public SyncResult write(Supplier supplier, List<PropertyListing> propertyListingList) {
        // 스윕이 이 시각을 기준으로 오래된 행을 걸러내므로 트랜잭션 안에서 한 번만 구한다.
        LocalDateTime startedAt = LocalDateTime.now(ZoneOffset.UTC);

        int roomTypeCount = 0;

        for (PropertyListing property: propertyListingList) {
            long propertyId = syncRepository.upsertProperty(
                    supplier, property.supplierCode(), startedAt
            );

            for (RoomTypeListing roomType : property.roomTypeListingList()) {
                syncRepository.upsertRoomType(propertyId, roomType.supplierCode(), startedAt);
                ++roomTypeCount;
            }
        }

        syncRepository.deactivateStale(supplier, startedAt);

        return SyncResult.ok(supplier, propertyListingList.size(), roomTypeCount);
    }
}
