package com.fourlane.auction.session.application;

import com.fourlane.auction.session.domain.PublicSessionCatalog;
import com.fourlane.auction.session.domain.SessionStatus;
import com.fourlane.global.config.ServerEpoch;
import com.fourlane.global.error.ApiException;
import com.fourlane.global.error.ErrorCode;
import java.time.Clock;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class PublicSessionQueryService {
    private final PublicSessionCatalog catalog;
    private final ServerEpoch epoch;
    private final Clock clock;
    public PublicSessionQueryService(PublicSessionCatalog catalog, ServerEpoch epoch, Clock clock) {
        this.catalog = catalog;
        this.epoch = epoch;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PublicSessionView session(long id, Long memberId) {
        requireReadSnapshot();
        var header = catalog.header(id).orElseThrow(() -> new ApiException(ErrorCode.SESSION_NOT_FOUND));
        // Approved M1-08 limitation. M2-04 must supply real RUNNING/FINISHED snapshots.
        if (header.getStatus() != SessionStatus.SCHEDULED) throw new ApiException(ErrorCode.INTERNAL_ERROR);
        var rows = catalog.rows(id);
        if (rows.stream().anyMatch(r -> !"READY".equals(r.getLaneStatus())
                || (r.getLotId() != null && !"PENDING".equals(r.getLotStatus()))))
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        var mine = memberId == null ? null : rows.stream()
                .filter(r -> r.getLotId() != null && memberId.equals(r.getSellerId()))
                .map(PublicSessionCatalog.Row::getLotId).toList();
        return new PublicSessionView(clock.instant(), epoch.value(), header, rows, mine);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PublicSessionView.Lot lot(long id) {
        requireReadSnapshot();
        var lot = catalog.lot(id).orElseThrow(() -> new ApiException(ErrorCode.LOT_NOT_FOUND));
        return new PublicSessionView.Lot(lot, catalog.images(id));
    }

    private static void requireReadSnapshot() {
        if (!TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !Objects.equals(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel(),
                        TransactionDefinition.ISOLATION_REPEATABLE_READ))
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
    }
}
