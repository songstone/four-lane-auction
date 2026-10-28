package com.fourlane.auction.policy.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.Repository;

public interface BidPolicyVersionCounterRepository extends Repository<BidPolicyVersionCounter, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from BidPolicyVersionCounter c where c.id = 1")
    Optional<BidPolicyVersionCounter> lock();
}
