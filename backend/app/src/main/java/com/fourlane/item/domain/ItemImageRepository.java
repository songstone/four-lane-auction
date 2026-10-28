package com.fourlane.item.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemImageRepository extends JpaRepository<ItemImage, Long> {
    Optional<ItemImage> findByStorageKey(String storageKey);
}
