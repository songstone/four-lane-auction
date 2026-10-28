package com.fourlane.auction.session.infra;
import com.fourlane.auction.session.domain.AdminSessionCatalog;
import java.util.*;
import org.springframework.stereotype.Repository;
@Repository
public class JpaAdminSessionCatalog implements AdminSessionCatalog {
    private final AdminSessionQueryRepository queries;
    public JpaAdminSessionCatalog(AdminSessionQueryRepository queries){this.queries=queries;}
    public Optional<Header> header(long id){return queries.header(id);}
    public List<Row> rows(long id){return queries.rows(id);}
}
