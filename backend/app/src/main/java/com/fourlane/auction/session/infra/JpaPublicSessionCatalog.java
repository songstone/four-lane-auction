package com.fourlane.auction.session.infra;

import com.fourlane.auction.session.domain.PublicSessionCatalog;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JpaPublicSessionCatalog implements PublicSessionCatalog {
    private final PublicSessionQueryRepository queries;
    public JpaPublicSessionCatalog(PublicSessionQueryRepository queries) { this.queries = queries; }
    public Optional<Header> header(long id) { return queries.header(id); }
    public List<Row> rows(long id) { return queries.rows(id); }
    public Optional<Lot> lot(long id) { return queries.lot(id); }
    public List<Image> images(long id) { return queries.images(id); }
}
