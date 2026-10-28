-- OQ-14: existing rows must satisfy the bounds; never rewrite historical settings.
ALTER TABLE auction_session ADD CONSTRAINT ck_session_timing_bounds CHECK (
    first_bid_wait_ms BETWEEN 1000 AND 60000 AND soft_close_ms BETWEEN 1000 AND 60000
    AND lot_gap_ms BETWEEN 1000 AND 60000 AND resume_min_ms BETWEEN 1000 AND 60000
);
