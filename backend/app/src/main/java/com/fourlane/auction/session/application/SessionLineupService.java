package com.fourlane.auction.session.application;

import com.fourlane.auction.session.domain.*;
import com.fourlane.global.error.*;
import com.fourlane.item.domain.ItemAssignmentAccess;
import java.time.Clock;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class SessionLineupService {
    private final SessionRepository sessions;
    private final SessionLotRepository lots;
    private final ItemAssignmentAccess items;
    private final AdminSessionQueryService queries;
    private final Clock clock;
    public SessionLineupService(SessionRepository sessions, SessionLotRepository lots, ItemAssignmentAccess items,
            AdminSessionQueryService queries, Clock clock) {
        this.sessions = sessions; this.lots = lots; this.items = items; this.queries = queries; this.clock = clock;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public AdminSessionView.Lane replace(long sessionId, int laneNo, List<Long> ids) {
        // REQUIRED may join an outer TX. Refuse silently inherited RR/read-only characteristics.
        if (!Objects.equals(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel(), TransactionDefinition.ISOLATION_READ_COMMITTED)
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) throw new ApiException(ErrorCode.INTERNAL_ERROR);
        if (sessions.reserveLineup(sessionId) != 1) {
            if (sessions.lock(sessionId).isEmpty()) throw new ApiException(ErrorCode.SESSION_NOT_FOUND);
            throw new ApiException(ErrorCode.SESSION_NOT_SCHEDULED);
        }
        var lane = queries.get(sessionId).lanes().stream().filter(l -> l.laneNo() == laneNo).findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.LANE_NOT_FOUND));
        var lockIds = new TreeSet<>(ids);
        lane.lots().forEach(l -> lockIds.add(l.itemId()));
        var missing = items.lock(lockIds);
        if (!missing.isEmpty()) throw ApiException.missingItems(missing);
        // RC creates this read view after all Item locks; a waiting request sees the winner's commit.
        var states = items.states(ids).stream().collect(Collectors.toMap(ItemAssignmentAccess.State::itemId, Function.identity()));
        var elsewhere = items.occupiedElsewhere(ids, lane.laneId());
        var rejected = new ArrayList<ApiErrorResponse.ItemStage>();
        for (long id : ids) {
            var state = states.get(id);
            if (state == null) throw ApiException.missingItems(List.of(id));
            boolean sameLane = state.stage().equals("SCHEDULED") && Objects.equals(state.laneId(), lane.laneId());
            if ((!state.stage().equals("APPROVED") && !sameLane) || elsewhere.contains(id))
                rejected.add(new ApiErrorResponse.ItemStage(id, state.stage()));
        }
        if (!rejected.isEmpty()) throw ApiException.unassignableItems(rejected);
        var at = clock.instant();
        if (states.values().stream().anyMatch(s -> s.reviewedAt() == null || at.isBefore(s.reviewedAt())))
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        lots.deleteLineup(lane.laneId()); // flush DELETE before reusing UNIQUE(lane_id, seq_no)
        for (int i = 0; i < ids.size(); i++) {
            var state = states.get(ids.get(i));
            lots.save(new SessionLot(lane.laneId(), state.itemId(), i + 1, state.startPrice(), state.reservePrice(), at));
        }
        lots.flush();
        sessions.touchLineup(sessionId, at);
        return queries.get(sessionId).lanes().stream().filter(l -> l.laneNo() == laneNo).findFirst().orElseThrow();
    }
}
