package com.fourlane.auction.session.application;

import com.fourlane.auction.session.domain.SessionStatus;
import com.fourlane.auction.session.domain.SessionListRow;
import com.fourlane.auction.session.domain.SessionCatalog;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionQueryService {
    private final SessionCatalog catalog;
    private final SessionCursorCodec cursors;
    private final Clock clock;

    public SessionQueryService(SessionCatalog catalog, SessionCursorCodec cursors, Clock clock) {
        this.catalog = catalog;
        this.cursors = cursors;
        this.clock = clock;
    }

    public record Result(Instant serverTime, List<SessionListRow> items, String nextCursor) {}

    @Transactional(readOnly = true)
    public Result list(SessionStatus status, int size, String cursor) {
        var after = cursors.decode(cursor, status);
        boolean first = after == null;
        Instant time = first ? null : after.time();
        long id = first ? 0 : after.id();
        List<SessionListRow> rows = catalog.list(status, first, time, id, size + 1);
        boolean more = rows.size() > size;
        var items = List.copyOf(rows.subList(0, Math.min(rows.size(), size)));
        String next = null;
        if (more) {
            var last = items.getLast();
            next = cursors.encode(status, last.sortTime(), last.getSessionId());
        }
        return new Result(clock.instant(), items, next);
    }
}
