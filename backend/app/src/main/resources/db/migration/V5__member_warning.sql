-- Auth's Member response reads the real warning count before trade implementation.
-- M3-07 MUST add fk_warning_trade after creating trade; do not recreate this table.
CREATE TABLE member_warning (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    member_id   BIGINT      NOT NULL,
    trade_id    BIGINT      NOT NULL,
    created_at  DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_warning_trade (trade_id),
    KEY idx_warning_member (member_id),
    CONSTRAINT fk_warning_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
