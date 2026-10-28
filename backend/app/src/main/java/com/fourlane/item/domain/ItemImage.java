package com.fourlane.item.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "item_image")
public class ItemImage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "item_id", nullable = false) private Long itemId;
    @Column(name = "sort_order", nullable = false, columnDefinition = "TINYINT") private int sortOrder;
    @Column(name = "storage_key", nullable = false, length = 200) private String storageKey;
    @Column(name = "content_type", nullable = false, length = 50) private String contentType;
    @Column(name = "size_bytes", nullable = false) private int sizeBytes;
    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(3)") private Instant createdAt;

    protected ItemImage() {}
    public ItemImage(long itemId, int sortOrder, ImageStorage.StoredImage image, Instant now) {
        this.itemId = itemId;
        this.sortOrder = sortOrder;
        this.storageKey = image.key();
        this.contentType = image.contentType();
        this.sizeBytes = image.sizeBytes();
        this.createdAt = now;
    }
    public String storageKey() { return storageKey; }
    public String contentType() { return contentType; }
    public int sizeBytes() { return sizeBytes; }
}
