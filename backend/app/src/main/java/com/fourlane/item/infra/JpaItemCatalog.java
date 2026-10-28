package com.fourlane.item.infra;

import com.fourlane.item.domain.ItemCatalog;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
public class JpaItemCatalog implements ItemCatalog {
    private final ItemQueryRepository queries;
    public JpaItemCatalog(ItemQueryRepository queries) { this.queries = queries; }
    @Override public List<Row> list(long sellerId, Instant afterTime, long afterId, int limit) {
        return queries.list(sellerId, afterTime, afterId, PageRequest.of(0, limit));
    }
}
