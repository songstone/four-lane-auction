package com.fourlane.auction.policy.infra;

import com.fourlane.auction.policy.domain.BidPolicyCatalog;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class JpaBidPolicyCatalog implements BidPolicyCatalog {
    private final BidPolicyQueryRepository queries;
    public JpaBidPolicyCatalog(BidPolicyQueryRepository queries) { this.queries = queries; }
    @Override public List<Row> all() { return queries.all(); }
    @Override public List<Row> byId(long policyId) { return queries.byId(policyId); }
}
