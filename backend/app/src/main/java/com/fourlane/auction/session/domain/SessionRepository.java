package com.fourlane.auction.session.domain;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.Repository;
public interface SessionRepository extends Repository<AuctionSession,Long> {
    // Matched rows (Connector/J useAffectedRows=false), including an unchanged SCHEDULED row.
    @Modifying
    @Query("update AuctionSession s set s.status = com.fourlane.auction.session.domain.SessionStatus.SCHEDULED where s.id = :id and s.status = com.fourlane.auction.session.domain.SessionStatus.SCHEDULED")
    int reserveLineup(long id);
    @Modifying
    @Query("update AuctionSession s set s.updatedAt = :at where s.id = :id")
    int touchLineup(long id, java.time.Instant at);
    AuctionSession save(AuctionSession value);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from AuctionSession s where s.id=:id")
    Optional<AuctionSession> lock(long id);
}
