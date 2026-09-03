package io.github.perardua.staysupply.sync;

import io.github.perardua.staysupply.supplier.Supplier;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.LocalDateTime;

public class SyncRepository {

    private final JdbcClient jdbcClient;

    public SyncRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public boolean hasAnyMapping() {
        return jdbcClient.sql("SELECT EXISTS(SELECT 1 FROM supplier_property)")
                .query(Boolean.class)
                .single();
    }

    public long upsertProperty(Supplier supplier, String supplierCode, LocalDateTime syncedAt) {
        jdbcClient.sql("""
                        INSERT INTO supplier_property (supplier, supplier_code, active, last_synced_at)
                        VALUES (:supplier, :supplierCode, 1, :syncedAt)
                        ON DUPLICATE KEY UPDATE active = 1, last_synced_at = :syncedAt
                        """)
                .param("supplier", supplier.name())
                .param("supplierCode", supplierCode)
                .param("syncedAt", syncedAt)
                .update();

        return jdbcClient.sql("""
                        SELECT id FROM supplier_property
                        WHERE supplier = :supplier AND supplier_code = :supplierCode
                        """)
                .param("supplier", supplier.name())
                .param("supplierCode", supplierCode)
                .query(Long.class)
                .single();
    }

    public void upsertRoomType(long propertyId, String supplierRoomCode, LocalDateTime syncedAt) {
        jdbcClient.sql("""
                        INSERT INTO supplier_room_type (property_id, supplier_room_code, active, last_synced_at)
                        VALUES (:propertyId, :supplierCode, 1, :syncedAt)
                        ON DUPLICATE KEY UPDATE active = 1, last_synced_at = :syncedAt
                        """)
                .param("propertyId", propertyId)
                .param("supplierCode", supplierRoomCode)
                .param("syncedAt", syncedAt)
                .update();
    }

    public int deactivateStale(Supplier supplier, LocalDateTime syncStartedAt) {
        int properties = jdbcClient.sql("""
                        UPDATE supplier_property
                        SET active = 0
                        WHERE supplier = :supplier AND last_synced_at < :startedAt AND active = 1
                        """)
                .param("supplier", supplier.name())
                .param("startedAt", syncStartedAt)
                .update();

        int roomTypes = jdbcClient.sql("""
                        UPDATE supplier_room_type rt
                        JOIN supplier_property p ON p.id = rt.property_id
                        SET rt.active = 0
                        WHERE p.supplier = :supplier AND rt.last_synced_at < :startedAt AND rt.active = 1
                        """)
                .param("supplier", supplier.name())
                .param("startedAt", syncStartedAt)
                .update();

        return properties + roomTypes;
    }
}
