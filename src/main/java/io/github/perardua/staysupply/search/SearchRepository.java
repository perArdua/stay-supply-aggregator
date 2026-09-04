package io.github.perardua.staysupply.search;

import io.github.perardua.staysupply.supplier.Supplier;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;

public class SearchRepository {

    private final JdbcClient jdbcClient;

    public SearchRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<RoomTypeMapping> findActiveMappings() {
        return jdbcClient.sql("""
                        SELECT p.supplier, p.supplier_code, p.id AS property_id,
                               rt.supplier_room_code, rt.id AS room_type_id
                        FROM supplier_property p
                        JOIN supplier_room_type rt ON rt.property_id = p.id
                        WHERE p.active = 1 AND rt.active = 1
                        """)
                .query((rs, rowNum) -> new RoomTypeMapping(
                        Supplier.valueOf(rs.getString("supplier")),
                        rs.getString("supplier_code"),
                        rs.getLong("property_id"),
                        rs.getString("supplier_room_code"),
                        rs.getLong("room_type_id")))
                .list();
    }
}
