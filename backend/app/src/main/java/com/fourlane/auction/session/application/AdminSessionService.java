package com.fourlane.auction.session.application;

import com.fourlane.auction.session.domain.*;
import com.fourlane.global.error.*;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminSessionService {
    public record Input(Integer laneCount, Instant scheduledStartAt, Map<String, Integer> timing) {}

    private final SessionRepository sessions;
    private final SessionLaneRepository lanes;
    private final SessionTimingDefaults defaults;
    private final AdminSessionQueryService queries;
    private final Clock clock;

    public AdminSessionService(SessionRepository sessions, SessionLaneRepository lanes,
            SessionTimingDefaults defaults, AdminSessionQueryService queries, Clock clock) {
        this.sessions = sessions;
        this.lanes = lanes;
        this.defaults = defaults;
        this.queries = queries;
        this.clock = clock;
    }

    @Transactional
    public AdminSessionView create(Input input) {
        var at = clock.instant();
        requireFuture(input.scheduledStartAt(), at);
        var session = sessions.save(new AuctionSession(input.laneCount(), input.scheduledStartAt(),
                defaults.timing().with(input.timing()), at));
        for (int no = 1; no <= input.laneCount(); no++) {
            lanes.save(new SessionLane(session.id(), no, at));
        }
        return queries.get(session.id());
    }

    @Transactional
    public AdminSessionView patch(long id, Input input) {
        // Do not load the entity before this locking read: a waiting editor needs the latest row.
        var session = sessions.lock(id).orElseThrow(() -> new ApiException(ErrorCode.SESSION_NOT_FOUND));
        if (session.status() != SessionStatus.SCHEDULED) {
            throw new ApiException(ErrorCode.SESSION_NOT_SCHEDULED);
        }
        var at = clock.instant();
        if (input.scheduledStartAt() != null) {
            requireFuture(input.scheduledStartAt(), at);
        }
        // Omitted schedule is preserved, even when that existing schedule is already past.
        session.revise(input.scheduledStartAt() == null ? session.scheduledStartAt() : input.scheduledStartAt(),
                session.timing().with(input.timing()), at);
        // JPQL flushes pending changes; the service proxy commits before the controller responds.
        return queries.get(id);
    }

    private static void requireFuture(Instant value, Instant at) {
        if (!value.isAfter(at)) {
            throw new ApiException(ErrorCode.SCHEDULED_START_IN_PAST);
        }
    }
}
