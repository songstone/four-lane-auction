package com.fourlane.item.infra;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("fourlane.image-storage")
public record ImageStorageProperties(Path root) {
    public ImageStorageProperties {
        if (root == null) throw new IllegalArgumentException("Image storage root is required");
        root = root.toAbsolutePath().normalize();
    }
}
