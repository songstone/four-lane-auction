package com.fourlane.item.infra;

import com.fourlane.global.error.ApiException;
import com.fourlane.global.error.ErrorCode;
import com.fourlane.item.domain.ImageStorage;
import java.io.*;
import java.nio.file.*;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(ImageStorageProperties.class)
public class LocalImageStorage implements ImageStorage {
    private static final Logger LOG = LoggerFactory.getLogger(LocalImageStorage.class);
    private final Path root;
    public LocalImageStorage(ImageStorageProperties properties) throws IOException {
        this.root = properties.root();
        Files.createDirectories(root);
    }
    @Override public StoredImage store(InputStream source) throws IOException {
        var input = new BufferedInputStream(source);
        input.mark(16);
        String contentType = ImageTypeDetector.detect(input.readNBytes(16));
        input.reset();
        String key = UUID.randomUUID().toString().replace("-", "");
        Path file = path(key);
        boolean created = false;
        try {
            long size = 0;
            try (var output = Files.newOutputStream(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                created = true;
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    size += count;
                    if (size > MAX_FILE_BYTES) throw new ApiException(ErrorCode.IMAGE_TOO_LARGE);
                    output.write(buffer, 0, count);
                }
            }
            return new StoredImage(key, contentType, (int) size);
        } catch (IOException | RuntimeException exception) {
            if (created) {
                try { Files.deleteIfExists(file); }
                catch (IOException cleanup) { LOG.error("Image cleanup failed key={}", key); }
            }
            throw exception;
        }
    }
    @Override public InputStream open(String key) throws IOException {
        Path file = path(key);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new NoSuchFileException(key);
        return Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS);
    }
    @Override public void delete(String key) throws IOException { Files.deleteIfExists(path(key)); }
    private Path path(String key) {
        if (key == null || !key.matches("[0-9a-f]{32}")) throw new ApiException(ErrorCode.IMAGE_NOT_FOUND);
        Path result = root.resolve(key).normalize();
        if (!result.startsWith(root)) throw new ApiException(ErrorCode.IMAGE_NOT_FOUND);
        return result;
    }
}
