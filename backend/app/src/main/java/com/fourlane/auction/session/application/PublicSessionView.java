package com.fourlane.auction.session.application;

import com.fourlane.auction.session.domain.PublicSessionCatalog;
import java.time.Instant;
import java.util.List;

public record PublicSessionView(Instant serverTime, String epoch, PublicSessionCatalog.Header header,
        List<PublicSessionCatalog.Row> rows, List<Long> myLotIds) {
    public record Lot(PublicSessionCatalog.Lot detail, List<PublicSessionCatalog.Image> images) {}
}
