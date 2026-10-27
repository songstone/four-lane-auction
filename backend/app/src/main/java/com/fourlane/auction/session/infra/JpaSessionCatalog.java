package com.fourlane.auction.session.infra;

import com.fourlane.auction.session.domain.SessionCatalog;
import com.fourlane.auction.session.domain.SessionListRow;
import com.fourlane.auction.session.domain.SessionStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
public class JpaSessionCatalog implements SessionCatalog {
    private final SessionQueryRepository repository;

    public JpaSessionCatalog(SessionQueryRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<SessionListRow> list(SessionStatus status, boolean firstPage, Instant afterTime, long afterId, int limit) {
        var page = PageRequest.of(0, limit);
        return switch (status) {
            case SCHEDULED -> repository.scheduled(status, firstPage, afterTime, afterId, page);
            case RUNNING -> repository.running(status, firstPage, afterTime, afterId, page);
            case FINISHED -> repository.finished(status, firstPage, afterTime, afterId, page);
        };
    }
}
