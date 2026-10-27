CREATE TABLE member (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    email             VARCHAR(255) NOT NULL,
    password_hash     VARCHAR(100) NOT NULL,          -- BCrypt
    nickname          VARCHAR(30)  NOT NULL,          -- 공개 메시지에는 마스킹해서만 나간다 (OQ-24)
    role              VARCHAR(10)  NOT NULL,          -- 역할은 하나만. USER 또는 ADMIN
    bid_suspended_at  DATETIME(3)  NULL,              -- 입찰 정지 (S-02). 해제 규칙은 OQ-26
    created_at        DATETIME(3)  NOT NULL,
    updated_at        DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_email (email),
    CONSTRAINT ck_member_role CHECK (role IN ('USER', 'ADMIN'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE refresh_token (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    member_id   BIGINT      NOT NULL,
    token_hash  CHAR(64)    NOT NULL,                 -- SHA-256 hex. 원문은 저장하지 않는다 (11장)
    expires_at  DATETIME(3) NOT NULL,
    revoked_at  DATETIME(3) NULL,                     -- 재발급(회전)으로 폐기한 시각
    created_at  DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token_hash (token_hash),
    KEY idx_refresh_token_member (member_id),
    CONSTRAINT fk_refresh_token_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
