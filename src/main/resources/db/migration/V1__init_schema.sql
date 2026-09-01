-- 공급사 코드 <-> 내부 식별자 매핑

CREATE TABLE supplier_property (
    id              BIGINT      NOT NULL AUTO_INCREMENT,           -- 내부 숙소 식별자
    supplier        VARCHAR(16) NOT NULL,                          -- 공급자
    supplier_code   VARCHAR(64) NOT NULL COLLATE utf8mb4_bin,      -- hotelCode / propertyId
    active          TINYINT(1)  NOT NULL DEFAULT 1,                -- last_synced_at을 기준으로 공급사 목록에 여전히 존재하면 1 아니면 0
    last_synced_at  DATETIME(6) NOT NULL,                          -- 공급사 목록에서 마지막으로 확인된 시각
    created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_supplier_property (supplier, supplier_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE supplier_room_type (
    id                  BIGINT      NOT NULL AUTO_INCREMENT,       -- 내부 객실 타입 식별자
    property_id         BIGINT      NOT NULL,                      -- supplier_property.id (논리적 FK)
    supplier_room_code  VARCHAR(64) NOT NULL COLLATE utf8mb4_bin,  -- roomTypeCode / roomId
    active              TINYINT(1)  NOT NULL DEFAULT 1,
    last_synced_at      DATETIME(6) NOT NULL,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_supplier_room_type (property_id, supplier_room_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
