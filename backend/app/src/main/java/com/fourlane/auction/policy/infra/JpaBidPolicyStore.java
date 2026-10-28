package com.fourlane.auction.policy.infra;

import com.fourlane.auction.policy.domain.*;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

@Repository
public class JpaBidPolicyStore implements BidPolicyStore {
    private final EntityManager entities;
    public JpaBidPolicyStore(EntityManager entities) { this.entities = entities; }
    @Override public void insert(BidPolicy policy) { entities.persist(policy); }
    @Override public void insert(BidPolicyTier tier) { entities.persist(tier); }
}
