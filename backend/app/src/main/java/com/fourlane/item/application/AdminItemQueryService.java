package com.fourlane.item.application;

import com.fourlane.global.error.*;
import com.fourlane.item.domain.AdminItemCatalog;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminItemQueryService {
    public record Page(List<AdminItemView> items, String nextCursor) {}
    private final AdminItemCatalog catalog;
    private final AdminItemCursorCodec cursors;
    public AdminItemQueryService(AdminItemCatalog catalog, AdminItemCursorCodec cursors) { this.catalog = catalog; this.cursors = cursors; }
    public Page list(String stage, int size, String cursor) {
        var p = cursors.decode(cursor, stage);
        var rows = catalog.list(stage, p == null ? null : p.time(), p == null ? 0 : p.id(), size + 1);
        var page = rows.subList(0, Math.min(size, rows.size()));
        String next = rows.size() > size ? cursors.encode(stage, page.getLast().getCreatedAt(), page.getLast().getItemId()) : null;
        return new Page(page.stream().map(AdminItemView::from).toList(), next);
    }
    public AdminItemView.Detail detail(long id) {
        var r = catalog.detail(id).orElseThrow(() -> new ApiException(ErrorCode.ITEM_NOT_FOUND));
        return new AdminItemView.Detail(AdminItemView.from(r), r.getDescription(), r.getReservePrice(), r.getRejectReason(),
                catalog.images(id).stream().map(i -> new AdminItemView.Image("/api/images/" + i.getStorageKey(), i.getSortOrder())).toList(),
                catalog.lots(id).stream().map(l -> new AdminItemView.Lot(l.getLotId(), l.getSessionId(), l.getLaneNo(), l.getSeqNo(), l.getStatus(), l.getFinalPrice())).toList());
    }
}
