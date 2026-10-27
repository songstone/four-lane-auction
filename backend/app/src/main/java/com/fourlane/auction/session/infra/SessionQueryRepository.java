package com.fourlane.auction.session.infra;

import com.fourlane.auction.session.domain.AuctionSession;
import com.fourlane.auction.session.domain.SessionListRow;
import com.fourlane.auction.session.domain.SessionStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface SessionQueryRepository extends Repository<AuctionSession, Long> {
    String SELECT = """
            select s.id as sessionId, s.status as status, s.laneCount as laneCount,
                   s.scheduledStartAt as scheduledStartAt, s.startedAt as startedAt,
                   s.finishedAt as finishedAt, count(lot.id) as lotCount
            from AuctionSession s
            left join SessionLane lane on lane.sessionId = s.id
            left join SessionLot lot on lot.laneId = lane.id
            """;
    String GROUP = """
            group by s.id, s.status, s.laneCount, s.scheduledStartAt, s.startedAt, s.finishedAt
            """;

    @Query(SELECT + """
            where s.status = :status and (:firstPage = true
                or s.scheduledStartAt > :afterTime
                or (s.scheduledStartAt = :afterTime and s.id > :afterId))
            """ + GROUP + "order by s.scheduledStartAt asc, s.id asc")
    List<SessionListRow> scheduled(@Param("status") SessionStatus status,
            @Param("firstPage") boolean firstPage, @Param("afterTime") Instant afterTime,
            @Param("afterId") long afterId, Pageable pageable);

    @Query(SELECT + """
            where s.status = :status and (:firstPage = true
                or (:afterTime is not null and (s.startedAt < :afterTime or s.startedAt is null))
                or ((s.startedAt = :afterTime or (s.startedAt is null and :afterTime is null))
                    and s.id < :afterId))
            """ + GROUP + "order by s.startedAt desc, s.id desc")
    List<SessionListRow> running(@Param("status") SessionStatus status,
            @Param("firstPage") boolean firstPage, @Param("afterTime") Instant afterTime,
            @Param("afterId") long afterId, Pageable pageable);

    @Query(SELECT + """
            where s.status = :status and (:firstPage = true
                or (:afterTime is not null and (s.finishedAt < :afterTime or s.finishedAt is null))
                or ((s.finishedAt = :afterTime or (s.finishedAt is null and :afterTime is null))
                    and s.id < :afterId))
            """ + GROUP + "order by s.finishedAt desc, s.id desc")
    List<SessionListRow> finished(@Param("status") SessionStatus status,
            @Param("firstPage") boolean firstPage, @Param("afterTime") Instant afterTime,
            @Param("afterId") long afterId, Pageable pageable);
}
