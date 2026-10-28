package com.fourlane.auction.policy.infra;

import com.fourlane.auction.policy.domain.*;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface BidPolicyQueryRepository extends Repository<BidPolicy, Long> {
    String SELECT = """
        select p.id as policyId, p.version as version, p.createdAt as createdAt,
               creator.nickname as createdByNickname, t.id.minPrice as minPrice, t.increment as increment
        from BidPolicy p join BidPolicyTier t on t.id.policyId = p.id
        left join Member creator on creator.id = p.createdBy
        """;
    @Query(SELECT + " order by p.version desc, t.id.minPrice asc")
    List<BidPolicyCatalog.Row> all();
    @Query(SELECT + " where p.id = :policyId order by t.id.minPrice asc")
    List<BidPolicyCatalog.Row> byId(long policyId);
}
