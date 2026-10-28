package com.fourlane.auction.session.domain;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.Repository;
public interface SessionRepository extends Repository<AuctionSession,Long> {
    AuctionSession save(AuctionSession value);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from AuctionSession s where s.id=:id")
    Optional<AuctionSession> lock(long id);
}
