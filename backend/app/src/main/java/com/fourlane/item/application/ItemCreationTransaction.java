package com.fourlane.item.application;

import com.fourlane.item.domain.*;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ItemCreationTransaction {
    public static final class Outcome implements TransactionSynchronization {
        private Integer status;
        private boolean started;
        @Override public void afterCompletion(int status) { this.started = true; this.status = status; }
        public boolean mayDelete() { return !started || Integer.valueOf(STATUS_ROLLED_BACK).equals(status); }
    }
    private final ItemRepository items;
    private final ItemImageRepository images;
    private final Clock clock;
    public ItemCreationTransaction(ItemRepository items, ItemImageRepository images, Clock clock) {
        this.items = items; this.images = images; this.clock = clock;
    }
    @Transactional
    public Item create(long sellerId, ItemRegistrationService.Command command,
            List<ImageStorage.StoredImage> stored, Outcome outcome) {
        outcome.started = true;
        TransactionSynchronizationManager.registerSynchronization(outcome);
        var now = clock.instant();
        var item = items.save(new Item(sellerId, command.name(), command.description(),
                command.startPrice(), command.reservePrice(), now));
        for (int i = 0; i < stored.size(); i++) images.save(new ItemImage(item.id(), i, stored.get(i), now));
        return item;
    }
}
