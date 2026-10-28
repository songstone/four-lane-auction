package com.fourlane.item.application;

import com.fourlane.global.error.*;
import com.fourlane.item.domain.*;
import java.io.IOException;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ItemRegistrationService {
    private static final Logger LOG = LoggerFactory.getLogger(ItemRegistrationService.class);
    public record Command(String name, String description, long startPrice, long reservePrice) {}
    private final ImageStorage storage;
    private final ItemCreationTransaction transaction;
    public ItemRegistrationService(ImageStorage storage, ItemCreationTransaction transaction) {
        this.storage = storage; this.transaction = transaction;
    }
    public Item register(long sellerId, Command command, List<ImageUpload> uploads) {
        validate(command, uploads);
        var stored = new ArrayList<ImageStorage.StoredImage>();
        var outcome = new ItemCreationTransaction.Outcome();
        try {
            for (var upload : uploads) {
                try (var input = upload.opener().open()) { stored.add(storage.store(input)); }
            }
            return transaction.create(sellerId, command, List.copyOf(stored), outcome);
        } catch (IOException | RuntimeException exception) {
            if (outcome.mayDelete()) {
                for (var image : stored) {
                    try { storage.delete(image.key()); }
                    catch (IOException | RuntimeException cleanup) { LOG.error("Image cleanup failed key={}", image.key()); }
                }
            } else LOG.error("Image batch retained after uncertain or committed transaction");
            if (exception instanceof RuntimeException runtime) throw runtime;
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }
    }
    public static void validate(Command command, List<ImageUpload> uploads) {
        if (command.name() == null || command.name().isBlank()) throw ApiException.invalidRequest("name", "REQUIRED");
        if (command.name().codePointCount(0, command.name().length()) > 100) throw ApiException.invalidRequest("name", "TOO_LONG");
        if (command.description() != null && command.description().codePointCount(0, command.description().length()) > 2000)
            throw ApiException.invalidRequest("description", "TOO_LONG");
        price(command.startPrice(), "startPrice");
        price(command.reservePrice(), "reservePrice");
        if (command.reservePrice() < command.startPrice()) throw ApiException.invalidRequest("reservePrice", "LESS_THAN_START_PRICE");
        if (uploads == null || uploads.isEmpty()) throw ApiException.invalidRequest("images", "REQUIRED");
        if (uploads.size() > ImageStorage.MAX_IMAGES) throw ApiException.invalidRequest("images", "TOO_MANY");
        if (uploads.stream().anyMatch(file -> file.size() > ImageStorage.MAX_FILE_BYTES)) throw new ApiException(ErrorCode.IMAGE_TOO_LARGE);
    }
    private static void price(long value, String field) {
        if (value < 1000 || value > 1_000_000_000) throw ApiException.invalidRequest(field, "OUT_OF_RANGE");
        if (value % 1000 != 0) throw ApiException.invalidRequest(field, "NOT_MULTIPLE_OF_1000");
    }
}
