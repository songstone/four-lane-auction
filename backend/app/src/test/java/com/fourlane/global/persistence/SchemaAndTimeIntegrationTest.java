package com.fourlane.global.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.fourlane.support.MemberTimeProbe;
import com.fourlane.support.MySqlIntegrationTest;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Import(SchemaAndTimeIntegrationTest.PersistenceProbes.class)
class SchemaAndTimeIntegrationTest extends MySqlIntegrationTest {
    private static final Instant FIXTURE_INSTANT = Instant.parse("2026-10-23T11:00:00.123Z");
    private static final String FIXTURE_TIME = "2026-10-23 11:00:00.123";
    private static final DateTimeFormatter MILLIS = new DateTimeFormatterBuilder().appendInstant(3).toFormatter();

    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @Autowired Flyway flyway;
    @Autowired Clock clock;

    @TestConfiguration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = {MemberTimeProbe.class,
            com.fourlane.member.domain.Member.class, com.fourlane.auth.domain.RefreshToken.class,
            com.fourlane.item.domain.Item.class,
            com.fourlane.auction.policy.domain.BidPolicy.class,
            com.fourlane.auction.policy.domain.BidPolicyTier.class,
            com.fourlane.auction.policy.domain.BidPolicyVersionCounter.class,
            com.fourlane.auction.session.domain.AuctionSession.class})
    static class PersistenceProbes {
    }

    @Test
    void appliesV1ThroughV7AndSeedsOnlyTheThreePolicyTiersAndCounter() {
        assertThat(jdbc.queryForObject("SELECT VERSION()", String.class)).startsWith("8.4.13");
        assertThat(jdbc.queryForList("""
                SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank
                """, String.class)).containsExactly("1", "2", "3", "4", "5", "6", "7");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history", Integer.class)).isEqualTo(7);
        assertThat(jdbc.queryForMap("SELECT id,last_version FROM bid_policy_version_counter"))
                .containsEntry("id", 1).containsEntry("last_version", 1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM member", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bid_increment_policy", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT id, version, created_by FROM bid_increment_policy"))
                .containsEntry("id", 1L).containsEntry("version", 1).containsEntry("created_by", null);
        assertThat(jdbc.query("""
                SELECT min_price, increment FROM bid_increment_tier WHERE policy_id = 1 ORDER BY min_price
                """, (row, index) -> List.of(row.getLong(1), row.getLong(2))))
                .containsExactly(List.of(0L, 1000L), List.of(10000L, 10000L), List.of(100000L, 25000L));
    }

    @Test
    void reapplyingMigrationsDoesNotChangeHistoryOrDuplicateThePolicy() {
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history", Integer.class)).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bid_increment_tier", Integer.class)).isEqualTo(3);
    }

    @Test
    void allTwelveApplicationTablesUseInnoDbAndAllTimeColumnsUseDatetime3WithoutDbTimeDefaults() {
        assertThat(jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'
                ORDER BY table_name
                """, String.class)).containsExactly("auction_session", "bid_increment_policy", "bid_increment_tier", "bid_policy_version_counter",
                "item", "item_image", "lane", "lane_transition", "lot", "member", "member_warning", "refresh_token");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'
                  AND (engine <> 'InnoDB' OR table_collation <> 'utf8mb4_0900_ai_ci')
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'
                  AND data_type IN ('datetime', 'timestamp')
                """, Integer.class)).isPositive();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'
                  AND data_type IN ('datetime', 'timestamp')
                  AND (data_type <> 'datetime' OR datetime_precision <> 3
                       OR column_default IS NOT NULL OR extra <> '')
                """, Integer.class)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10-23T11:00:00.000Z", "2026-10-23T11:00:00.123Z", "2026-10-23T11:00:14.999Z"})
    @Transactional
    void hibernateRoundTripsMillisecondsAndStoresTheActualUtcWallTime(String value) {
        assertRoundTrip(Instant.parse(value));
    }

    @Test
    @Transactional
    void tickClockTruncatesBeforePersistenceAndDoesNotRoundAcrossTheDeadline() {
        Instant deadline = Instant.parse("2026-10-23T11:00:15.000Z");
        Clock tick = Clock.tick(Clock.fixed(Instant.parse("2026-10-23T11:00:14.999600Z"), ZoneOffset.UTC),
                Duration.ofMillis(1));
        Instant acceptedAt = tick.instant();
        assertThat(acceptedAt).isEqualTo(Instant.parse("2026-10-23T11:00:14.999Z")).isBefore(deadline);
        assertRoundTrip(acceptedAt);
    }

    @Test
    @Transactional
    void injectedClockValueRoundTripsAndTheSeparateJvmUsesTheExpectedDefaultTimeZone() {
        assertThat(ZoneId.systemDefault().getId()).isEqualTo(System.getProperty("fourlane.expected-time-zone"));
        assertThat(jdbc.queryForObject("SELECT @@session.time_zone", String.class)).isIn("+00:00", "UTC");
        Instant time = clock.instant();
        assertThat(time.getNano() % 1_000_000).isZero();
        assertRoundTrip(time);
    }

    @Test
    @Transactional
    void itemStartPriceCheckRejects12345AfterAValidInsertSucceeds() {
        long memberId = insertMember();
        insertItem(memberId, 12000);
        assertCheckViolation("ck_item_start_price", () -> insertItem(memberId, 12345));
    }

    @Test
    @Transactional
    void pausedLaneRequiresANonNullCauseWhileOperatorPauseIsAccepted() {
        long sessionId = insertSession();
        insertLane(sessionId, 1, "PAUSED", "OPERATOR");
        assertCheckViolation("ck_lane_pause", () -> insertLane(sessionId, 2, "PAUSED", null));
    }

    @Test
    @Transactional
    void unsoldLotRequiresANonNullReasonWhileNoBidIsAccepted() {
        long itemId = insertItem(insertMember(), 12000);
        long laneId = insertLane(insertSession(), 1, "READY", null);
        insertUnsoldLot(laneId, itemId, 1, "NO_BID");
        assertCheckViolation("ck_lot_unsold_reason", () -> insertUnsoldLot(laneId, itemId, 2, null));
    }

    private void assertRoundTrip(Instant expected) {
        var probe = new MemberTimeProbe(expected);
        entityManager.persist(probe);
        entityManager.flush();
        entityManager.clear();
        var reloaded = entityManager.find(MemberTimeProbe.class, probe.id());
        assertThat(reloaded.createdAt()).isEqualTo(expected);
        assertThat(reloaded.updatedAt()).isEqualTo(expected);
        // Prevent a symmetric driver conversion from hiding a nine-hour storage error.
        String storedUtc = jdbc.queryForObject("""
                SELECT CONCAT(DATE_FORMAT(created_at, '%Y-%m-%dT%H:%i:%s.%f'), 'Z') FROM member WHERE id = ?
                """, String.class, probe.id());
        assertThat(storedUtc).isEqualTo(MILLIS.format(expected).replace("Z", "000Z"));
    }

    private long insertMember() {
        var probe = new MemberTimeProbe(FIXTURE_INSTANT);
        entityManager.persist(probe);
        entityManager.flush();
        return probe.id();
    }

    private long insertItem(long sellerId, long startPrice) {
        jdbc.update("""
                INSERT INTO item (seller_id, name, start_price, reserve_price, review_status, created_at, updated_at)
                VALUES (?, 'CHECK fixture', ?, 20000, 'APPROVED', ?, ?)
                """, sellerId, startPrice, FIXTURE_TIME, FIXTURE_TIME);
        return lastInsertId();
    }

    private long insertSession() {
        jdbc.update("""
                INSERT INTO auction_session (status, lane_count, scheduled_start_at, first_bid_wait_ms,
                    soft_close_ms, lot_gap_ms, resume_min_ms, created_at, updated_at)
                VALUES ('SCHEDULED', 2, ?, 15000, 10000, 5000, 10000, ?, ?)
                """, FIXTURE_TIME, FIXTURE_TIME, FIXTURE_TIME);
        return lastInsertId();
    }

    private long insertLane(long sessionId, int laneNo, String status, String cause) {
        jdbc.update("""
                INSERT INTO lane (session_id, lane_no, status, pause_cause, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, sessionId, laneNo, status, cause, FIXTURE_TIME, FIXTURE_TIME);
        return lastInsertId();
    }

    private void insertUnsoldLot(long laneId, long itemId, int seqNo, String reason) {
        jdbc.update("""
                INSERT INTO lot (lane_id, item_id, seq_no, status, start_price, reserve_price, current_price,
                    bid_count, bidding_started_at, closed_at, unsold_reason, created_at, updated_at)
                VALUES (?, ?, ?, 'UNSOLD', 12000, 20000, 12000, 0, ?, ?, ?, ?, ?)
                """, laneId, itemId, seqNo, FIXTURE_TIME, FIXTURE_TIME, reason, FIXTURE_TIME, FIXTURE_TIME);
    }

    private long lastInsertId() {
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void assertCheckViolation(String constraint, Runnable insert) {
        DataAccessException failure = catchThrowableOfType(DataAccessException.class, insert::run);
        assertThat(failure).isNotNull();
        assertThat(failure.getMostSpecificCause()).isInstanceOfSatisfying(SQLException.class, sql -> {
            assertThat(sql.getErrorCode()).isEqualTo(3819);
            assertThat(sql.getMessage()).contains(constraint);
        });
    }
}
