package com.fourlane.item.domain;

import java.io.IOException;
import java.io.InputStream;

public interface ImageStorage {
    int MAX_IMAGES = 10;
    long MAX_FILE_BYTES = 10_000_000;
    record StoredImage(String key, String contentType, int sizeBytes) {}
    StoredImage store(InputStream input) throws IOException;
    InputStream open(String key) throws IOException;
    void delete(String key) throws IOException;
}
