package com.fourlane.item.infra;

import com.fourlane.item.domain.*;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class JpaItemAssignmentAccess implements ItemAssignmentAccess {
    private final ItemRepository items;
    private final ItemAssignmentQueryRepository queries;
    public JpaItemAssignmentAccess(ItemRepository items, ItemAssignmentQueryRepository queries) {
        this.items = items; this.queries = queries;
    }
    public List<Long> lock(Collection<Long> ids) {
        var missing = new ArrayList<Long>();
        // One PK read per ID makes the lock acquisition order explicit, independent of query plans.
        for (long id : new TreeSet<>(ids)) if (items.findForUpdate(id).isEmpty()) missing.add(id);
        return List.copyOf(missing);
    }
    public List<State> states(Collection<Long> ids) {
        if (ids.isEmpty()) return List.of();
        return queries.states(ids).stream().map(r -> new State(r.getItemId(), r.getStage(), r.getLaneId(),
                r.getStartPrice(), r.getReservePrice(), r.getReviewedAt())).toList();
    }
    public Set<Long> occupiedElsewhere(Collection<Long> ids, long laneId) {
        return ids.isEmpty() ? Set.of() : Set.copyOf(queries.occupiedElsewhere(ids, laneId));
    }
}
