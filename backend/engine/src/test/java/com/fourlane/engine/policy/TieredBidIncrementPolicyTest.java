package com.fourlane.engine.policy;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TieredBidIncrementPolicyTest {
    private static List<TieredBidIncrementPolicy.Tier> defaults() {
        return List.of(new TieredBidIncrementPolicy.Tier(0, 1000), new TieredBidIncrementPolicy.Tier(10000, 10000),
                new TieredBidIncrementPolicy.Tier(100000, 25000));
    }
    @ParameterizedTest
    @CsvSource({"0,1000", "9999,10999", "10000,20000", "50000,60000", "99999,109999", "100000,125000", "100001,125001"})
    void calculatesAtInclusiveBoundariesAndBeforeTheFirstBid(long current, long expected) {
        assertEquals(expected, new TieredBidIncrementPolicy(defaults()).nextPrice(current));
    }
    @Test void defensivelyCopiesTiersAndDoesNotRequireRoundedBoundaries() {
        var source = new ArrayList<>(List.of(new TieredBidIncrementPolicy.Tier(0, 1000), new TieredBidIncrementPolicy.Tier(9999, 2000)));
        var policy = new TieredBidIncrementPolicy(source);
        source.clear();
        assertEquals(10998, policy.nextPrice(9998));
        assertEquals(11999, policy.nextPrice(9999));
    }
    @Test void rejectsInvalidPoliciesInsteadOfSortingOrRepairingThem() {
        for (var tiers : List.of(List.<TieredBidIncrementPolicy.Tier>of(),
                List.of(new TieredBidIncrementPolicy.Tier(1, 1000)),
                List.of(new TieredBidIncrementPolicy.Tier(0, 0)),
                List.of(new TieredBidIncrementPolicy.Tier(0, -1000)),
                List.of(new TieredBidIncrementPolicy.Tier(0, 1001)),
                List.of(new TieredBidIncrementPolicy.Tier(0, 1000), new TieredBidIncrementPolicy.Tier(0, 2000)),
                List.of(new TieredBidIncrementPolicy.Tier(0, 1000), new TieredBidIncrementPolicy.Tier(10000, 2000), new TieredBidIncrementPolicy.Tier(9999, 3000))))
            assertThrows(IllegalArgumentException.class, () -> new TieredBidIncrementPolicy(tiers));
    }
    @Test void neverWrapsAnOverflowOrAcceptsANegativeCurrentPrice() {
        var policy = new TieredBidIncrementPolicy(defaults());
        assertThrows(ArithmeticException.class, () -> policy.nextPrice(Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> policy.nextPrice(-1));
    }
}
