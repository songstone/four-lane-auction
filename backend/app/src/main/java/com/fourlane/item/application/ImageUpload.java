package com.fourlane.item.application;

import java.io.IOException;
import java.io.InputStream;

public record ImageUpload(long size, Opener opener) {
    @FunctionalInterface public interface Opener { InputStream open() throws IOException; }
}
