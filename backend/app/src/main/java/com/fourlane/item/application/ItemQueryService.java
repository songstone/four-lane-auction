package com.fourlane.item.application;

import com.fourlane.global.error.*;
import com.fourlane.item.domain.*;
import java.io.*;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ItemQueryService {
    public record Result(List<ItemView> items, String nextCursor) {}
    public record Image(String contentType, int sizeBytes, InputStream input) {}
    private final ItemCatalog catalog;
    private final ItemCursorCodec cursors;
    private final ItemImageRepository images;
    private final ImageStorage storage;
    public ItemQueryService(ItemCatalog catalog, ItemCursorCodec cursors, ItemImageRepository images, ImageStorage storage) {
        this.catalog = catalog; this.cursors = cursors; this.images = images; this.storage = storage;
    }
    @Transactional(readOnly = true)
    public Result list(long sellerId, int size, String cursor) {
        var position = cursors.decode(cursor, sellerId);
        var rows = catalog.list(sellerId, position == null ? null : position.time(), position == null ? 0 : position.id(), size + 1);
        var page = rows.subList(0, Math.min(rows.size(), size));
        String next = rows.size() > size ? cursors.encode(sellerId, page.getLast().getCreatedAt(), page.getLast().getItemId()) : null;
        return new Result(page.stream().map(row -> new ItemView(row.getItemId(), row.getName(),
                "/api/images/" + row.getStorageKey(), row.getStartPrice(), row.getReviewStatus(),
                row.getRejectReason(), row.getReviewedAt(), row.getCreatedAt())).toList(), next);
    }
    @Transactional(readOnly = true)
    public Image image(String key) {
        if (!key.matches("[0-9a-f]{32}")) throw new ApiException(ErrorCode.IMAGE_NOT_FOUND);
        var image = images.findByStorageKey(key).orElseThrow(() -> new ApiException(ErrorCode.IMAGE_NOT_FOUND));
        try { return new Image(image.contentType(), image.sizeBytes(), storage.open(image.storageKey())); }
        catch (FileNotFoundException | java.nio.file.NoSuchFileException exception) { throw new ApiException(ErrorCode.IMAGE_NOT_FOUND); }
        catch (IOException exception) { throw new ApiException(ErrorCode.INTERNAL_ERROR); }
    }
}
