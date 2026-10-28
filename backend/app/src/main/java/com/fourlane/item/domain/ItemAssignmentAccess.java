package com.fourlane.item.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Read/lock port. Auction never writes Item data. Locks live in the caller's transaction. */
public interface ItemAssignmentAccess {
    record State(long itemId, String stage, Long laneId, long startPrice, long reservePrice, Instant reviewedAt) {}
    List<Long> lock(Collection<Long> itemIds);
    List<State> states(Collection<Long> itemIds);
    Set<Long> occupiedElsewhere(Collection<Long> itemIds, long laneId);
}
