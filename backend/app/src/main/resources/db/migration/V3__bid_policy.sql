CREATE TABLE bid_increment_policy (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    version     INT         NOT NULL,
    created_by  BIGINT      NULL,                     -- 시드로 넣은 v1은 NULL
    created_at  DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bid_policy_version (version),
    CONSTRAINT fk_bid_policy_creator FOREIGN KEY (created_by) REFERENCES member (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE bid_increment_tier (
    policy_id  BIGINT NOT NULL,
    min_price  BIGINT NOT NULL,                       -- 현재가가 이 값 이상이면 적용 (구간 하한, 포함)
    increment  BIGINT NOT NULL,
    PRIMARY KEY (policy_id, min_price),
    CONSTRAINT fk_bid_tier_policy FOREIGN KEY (policy_id) REFERENCES bid_increment_policy (id),
    CONSTRAINT ck_bid_tier_min CHECK (min_price >= 0),
    CONSTRAINT ck_bid_tier_increment CHECK (increment > 0 AND increment % 1000 = 0)  -- 모든 가격이 1,000원 단위로 유지된다 (PRD 6.3)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 기본 정책 v1 (PRD 6.3 가격 규칙)
INSERT INTO bid_increment_policy (id, version, created_by, created_at)
VALUES (1, 1, NULL, '2026-10-23 00:00:00.000');

INSERT INTO bid_increment_tier (policy_id, min_price, increment)
VALUES (1, 0, 1000),
       (1, 10000, 10000),
       (1, 100000, 25000);
