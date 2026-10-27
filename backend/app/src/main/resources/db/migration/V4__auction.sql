CREATE TABLE auction_session (
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    status              VARCHAR(20) NOT NULL,
    lane_count          TINYINT     NOT NULL,
    scheduled_start_at  DATETIME(3) NOT NULL,         -- 필수 (OQ-03)
    first_bid_wait_ms   INT         NOT NULL,         -- 기본 15000
    soft_close_ms       INT         NOT NULL,         -- 기본 10000
    lot_gap_ms          INT         NOT NULL,         -- 기본 5000
    resume_min_ms       INT         NOT NULL,         -- 재개 시 최소 보장 시간, 기본 10000
    bid_policy_id       BIGINT      NULL,             -- 회차 시작 순간 고정. SCHEDULED 동안은 NULL
    started_at          DATETIME(3) NULL,
    finished_at         DATETIME(3) NULL,
    created_at          DATETIME(3) NOT NULL,
    updated_at          DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_session_status_start (status, scheduled_start_at),
    CONSTRAINT fk_session_policy FOREIGN KEY (bid_policy_id) REFERENCES bid_increment_policy (id),
    CONSTRAINT ck_session_status CHECK (status IN ('SCHEDULED', 'RUNNING', 'FINISHED')),
    CONSTRAINT ck_session_lane_count CHECK (lane_count BETWEEN 1 AND 4),
    CONSTRAINT ck_session_params CHECK (first_bid_wait_ms > 0 AND soft_close_ms > 0
                                        AND lot_gap_ms > 0 AND resume_min_ms > 0),  -- 허용 범위는 OQ-14
    CONSTRAINT ck_session_policy_fixed CHECK (status = 'SCHEDULED' OR bid_policy_id IS NOT NULL)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE lane (
    id                   BIGINT      NOT NULL AUTO_INCREMENT,
    session_id           BIGINT      NOT NULL,
    lane_no              TINYINT     NOT NULL,
    status               VARCHAR(20) NOT NULL,
    paused_remaining_ms  INT         NULL,            -- 입찰 단계에서 정지할 때 저장한 마감까지 남은 시간 (T5). 간격 중 정지와 복구 정지는 NULL
    pause_cause          VARCHAR(20) NULL,            -- PAUSED일 때 정지 원인. 복구는 이미 PAUSED인 레인의 원인을 덮어쓰지 않는다 (OQ-43)
    finished_at          DATETIME(3) NULL,
    created_at           DATETIME(3) NOT NULL,
    updated_at           DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_lane_session_no (session_id, lane_no),
    KEY idx_lane_status (status),                     -- 재시작 복구 대상 조회
    CONSTRAINT fk_lane_session FOREIGN KEY (session_id) REFERENCES auction_session (id),
    CONSTRAINT ck_lane_no CHECK (lane_no BETWEEN 1 AND 4),
    CONSTRAINT ck_lane_status CHECK (status IN ('READY', 'RUNNING', 'PAUSED', 'FINISHED')),
    CONSTRAINT ck_lane_pause CHECK (
        (status = 'PAUSED' AND pause_cause IS NOT NULL
                           AND pause_cause IN ('OPERATOR', 'SESSION_BULK', 'RECOVERY')
                           AND (paused_remaining_ms IS NULL OR paused_remaining_ms > 0))
        OR (status <> 'PAUSED' AND pause_cause IS NULL AND paused_remaining_ms IS NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE lot (
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    lane_id             BIGINT      NOT NULL,
    item_id             BIGINT      NOT NULL,
    seq_no              INT         NOT NULL,         -- 레인 안의 순번, 1부터
    status              VARCHAR(20) NOT NULL,
    start_price         BIGINT      NOT NULL,         -- 편성할 때 item에서 복사
    reserve_price       BIGINT      NOT NULL,         -- 편성할 때 복사. 공개 DTO에 없다
    current_price       BIGINT      NOT NULL,         -- 입찰 전에는 start_price
    highest_bidder_id   BIGINT      NULL,
    bid_count           INT         NOT NULL,
    last_bid_id         BIGINT      NULL,             -- 마지막 수락 입찰(bid.id). 메시지의 lastBidId (7.2). bid → lot과 순환이라 FK는 두지 않는다
    bidding_started_at  DATETIME(3) NULL,             -- t0. INV-4 재계산의 시작점
    deadline_at         DATETIME(3) NULL,             -- BIDDING 동안의 현재 마감 시각
    closed_at           DATETIME(3) NULL,             -- SOLD 또는 UNSOLD가 된 시각 (T4 간격의 기준)
    unsold_reason       VARCHAR(20) NULL,             -- INV-5: 유찰 사유는 셋 중 하나
    created_at          DATETIME(3) NOT NULL,
    updated_at          DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_lot_lane_seq (lane_id, seq_no),
    KEY idx_lot_item (item_id),
    CONSTRAINT fk_lot_lane FOREIGN KEY (lane_id) REFERENCES lane (id),
    CONSTRAINT fk_lot_item FOREIGN KEY (item_id) REFERENCES item (id),
    CONSTRAINT fk_lot_highest_bidder FOREIGN KEY (highest_bidder_id) REFERENCES member (id),
    CONSTRAINT ck_lot_status CHECK (status IN ('PENDING', 'BIDDING', 'SOLD', 'UNSOLD')),
    CONSTRAINT ck_lot_seq_no CHECK (seq_no >= 1),
    CONSTRAINT ck_lot_prices CHECK (start_price > 0 AND start_price % 1000 = 0
                                    AND reserve_price >= start_price AND current_price >= start_price),
    CONSTRAINT ck_lot_unsold_reason CHECK (
        (status = 'UNSOLD' AND unsold_reason IS NOT NULL
                           AND unsold_reason IN ('NO_BID', 'RESERVE_NOT_MET', 'FORCE_CLOSED'))
        OR (status <> 'UNSOLD' AND unsold_reason IS NULL)),
    CONSTRAINT ck_lot_sold_winner CHECK (status <> 'SOLD' OR highest_bidder_id IS NOT NULL),
    CONSTRAINT ck_lot_last_bid CHECK ((bid_count = 0 AND last_bid_id IS NULL)
                                      OR (bid_count > 0 AND last_bid_id IS NOT NULL)),
    CONSTRAINT ck_lot_started CHECK (status = 'PENDING' OR bidding_started_at IS NOT NULL),
    CONSTRAINT ck_lot_closed CHECK (status NOT IN ('SOLD', 'UNSOLD') OR closed_at IS NOT NULL)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 레인 상태 전이 이력 (추가 전용). INV-4 재계산에 필요하다.
-- 정지·재개 이력을 감사 로그(S-03, Should)에만 맡기면 감사 로그가 일정에서 빠질 때 검증이 멈춘다 (CHALLENGES 2장).
CREATE TABLE lane_transition (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    lane_id       BIGINT      NOT NULL,
    lot_id        BIGINT      NULL,                   -- 전이 순간의 현재 Lot (간격 중이면 직전 Lot)
    to_status     VARCHAR(20) NOT NULL,
    cause         VARCHAR(30) NOT NULL,
    remaining_ms  INT         NULL,                   -- PAUSED로 갈 때 저장한 남은 시간 (T5)
    occurred_at   DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_lane_transition_lane (lane_id, occurred_at),
    CONSTRAINT fk_lane_transition_lane FOREIGN KEY (lane_id) REFERENCES lane (id),
    CONSTRAINT fk_lane_transition_lot FOREIGN KEY (lot_id) REFERENCES lot (id),
    CONSTRAINT ck_lane_transition_status CHECK (to_status IN ('RUNNING', 'PAUSED', 'FINISHED')),
    CONSTRAINT ck_lane_transition_cause CHECK (cause IN (
        'SESSION_START',     -- 회차 시작
        'OPERATOR',          -- 운영자 레인 정지·재개
        'SESSION_BULK',      -- 회차 일괄 정지·재개 (OQ-13 판단에 필요해 구분한다)
        'RECOVERY',          -- 서버 재시작 복구
        'LAST_LOT_DONE',     -- 마지막 Lot 종료 + 간격
        'FORCE_CLOSE_LAST'   -- 정지 중 마지막 Lot 강제 종료
    ))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
