package com.fourlane.auction.session.domain;

import org.springframework.data.jpa.repository.*;

public interface SessionLotRepository extends JpaRepository<SessionLot, Long> {
    @Modifying(flushAutomatically=true, clearAutomatically=true)
    @Query("delete from SessionLot o where o.laneId = :laneId")
    int deleteLineup(long laneId);
}
