CREATE TABLE item (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    seller_id      BIGINT       NOT NULL,
    name           VARCHAR(100) NOT NULL,
    description    TEXT         NULL,
    start_price    BIGINT       NOT NULL,
    reserve_price  BIGINT       NOT NULL,             -- 입력하지 않으면 start_price와 같은 값 (OQ-21). 공개 DTO에 없다
    review_status  VARCHAR(20)  NOT NULL,
    reject_reason  VARCHAR(500) NULL,                 -- U-02에 반려 사유 표시
    reviewed_at    DATETIME(3)  NULL,
    created_at     DATETIME(3)  NOT NULL,
    updated_at     DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_item_seller (seller_id, id),
    KEY idx_item_review_status (review_status, id),
    CONSTRAINT fk_item_seller FOREIGN KEY (seller_id) REFERENCES member (id),
    CONSTRAINT ck_item_start_price CHECK (start_price > 0 AND start_price % 1000 = 0),
    CONSTRAINT ck_item_reserve_price CHECK (reserve_price >= start_price AND reserve_price % 1000 = 0),
    CONSTRAINT ck_item_review_status CHECK (review_status IN ('PENDING_REVIEW', 'APPROVED', 'REJECTED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE item_image (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    item_id       BIGINT       NOT NULL,
    sort_order    TINYINT      NOT NULL,              -- 0이 레인 패널에 보이는 대표 이미지
    storage_key   VARCHAR(200) NOT NULL,              -- 서버가 만든 무작위 키. 사용자 파일명은 쓰지 않는다
    content_type  VARCHAR(50)  NOT NULL,
    size_bytes    INT          NOT NULL,
    created_at    DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_image_order (item_id, sort_order),
    UNIQUE KEY uk_item_image_key (storage_key),
    CONSTRAINT fk_item_image_item FOREIGN KEY (item_id) REFERENCES item (id),
    CONSTRAINT ck_item_image_order CHECK (sort_order >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
