package com.fourlane.auction.policy.application;

import com.fourlane.auction.policy.domain.*;
import com.fourlane.global.error.ApiException;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BidPolicyService {
    public static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;
    public record InputTier(Long minPrice, Long increment) {}
    private final BidPolicyVersionCounterRepository counters;
    private final BidPolicyStore store;
    private final BidPolicyCatalog catalog;
    private final Clock clock;
    public BidPolicyService(BidPolicyVersionCounterRepository counters, BidPolicyStore store,
            BidPolicyCatalog catalog, Clock clock) {
        this.counters = counters; this.store = store; this.catalog = catalog; this.clock = clock;
    }

    @Transactional
    public BidPolicyView create(long creator, List<InputTier> tiers) {
        validate(tiers);
        var counter = counters.lock().orElseThrow(() -> new IllegalStateException("Policy counter missing"));
        var at = clock.instant();
        var policy = new BidPolicy(counter.allocate(), creator, at);
        store.insert(policy);
        for (var tier : tiers) store.insert(new BidPolicyTier(policy.id(), tier.minPrice(), tier.increment()));
        return view(catalog.byId(policy.id()), true);
    }

    @Transactional(readOnly = true)
    public List<BidPolicyView> list() {
        var rows = catalog.all();
        if (rows.isEmpty()) return List.of();
        int latest = rows.getFirst().getVersion();
        var groups = new LinkedHashMap<Long, List<BidPolicyCatalog.Row>>();
        for (var row : rows) groups.computeIfAbsent(row.getPolicyId(), ignored -> new ArrayList<>()).add(row);
        return groups.values().stream().map(group -> view(group, group.getFirst().getVersion() == latest)).toList();
    }

    public static void validate(List<InputTier> tiers) {
        if (tiers == null || tiers.isEmpty()) throw ApiException.invalidRequest("tiers", "REQUIRED");
        var seen = new HashSet<Long>();
        long previous = -1;
        for (int i = 0; i < tiers.size(); i++) {
            var tier = tiers.get(i);
            String field = "tiers[" + i + "]";
            if (tier == null) throw ApiException.invalidRequest(field, "REQUIRED");
            range(tier.minPrice(), field + ".minPrice", 0);
            range(tier.increment(), field + ".increment", 1);
            if (i == 0 && tier.minPrice() != 0) throw ApiException.invalidRequest(field + ".minPrice", "OUT_OF_RANGE");
            if (!seen.add(tier.minPrice())) throw ApiException.invalidRequest(field + ".minPrice", "DUPLICATE");
            if (tier.minPrice() <= previous) throw ApiException.invalidRequest(field + ".minPrice", "INVALID_FORMAT");
            if (tier.increment() % 1000 != 0) throw ApiException.invalidRequest(field + ".increment", "NOT_MULTIPLE_OF_1000");
            previous = tier.minPrice();
        }
    }
    private static void range(Long value, String field, long minimum) {
        if (value == null) throw ApiException.invalidRequest(field, "REQUIRED");
        if (value < minimum || value > MAX_SAFE_INTEGER) throw ApiException.invalidRequest(field, "OUT_OF_RANGE");
    }
    private static BidPolicyView view(List<BidPolicyCatalog.Row> rows, boolean latest) {
        if (rows.isEmpty()) throw new IllegalStateException("Policy tiers missing");
        var first = rows.getFirst();
        return new BidPolicyView(first.getPolicyId(), first.getVersion(), rows.stream()
                .map(row -> new BidPolicyView.Tier(row.getMinPrice(), row.getIncrement())).toList(),
                first.getCreatedAt(), first.getCreatedByNickname(), latest);
    }
}
