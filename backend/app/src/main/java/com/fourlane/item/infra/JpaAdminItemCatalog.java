package com.fourlane.item.infra;

import com.fourlane.item.domain.AdminItemCatalog;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
public class JpaAdminItemCatalog implements AdminItemCatalog {
    private final AdminItemQueryRepository queries;
    public JpaAdminItemCatalog(AdminItemQueryRepository queries) { this.queries = queries; }
    public List<Row> list(String stage, Instant time, long id, int limit) {
        var page = PageRequest.of(0, limit);
        return stage.equals("PENDING_REVIEW") ? queries.ascending(stage, time, id, page) : queries.descending(stage, time, id, page);
    }
    public Optional<DetailRow> detail(long id) { return queries.detail(id); }
    public List<ImageRow> images(long id) { return queries.images(id); }
    public List<LotRow> lots(long id) { return queries.lots(id); }
}
