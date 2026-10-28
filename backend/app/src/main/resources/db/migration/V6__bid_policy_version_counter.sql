CREATE TABLE bid_policy_version_counter (
    id            INT NOT NULL,
    last_version  INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_bid_policy_counter_singleton CHECK (id = 1),
    CONSTRAINT ck_bid_policy_counter_positive CHECK (last_version >= 1)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO bid_policy_version_counter (id, last_version)
SELECT 1, MAX(version) FROM bid_increment_policy;
