package com.fourlane.item;

import static org.assertj.core.api.Assertions.*;
import com.fourlane.global.error.ApiException;
import com.fourlane.item.infra.*;
import com.fourlane.support.ImageFixtures;
import java.io.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalImageStorageTest {
    @TempDir Path root;
    @Test void roundTripsWithRandomImmutableKeys() throws Exception {
        var storage = new LocalImageStorage(new ImageStorageProperties(root));
        var a = storage.store(new ByteArrayInputStream(ImageFixtures.PNG));
        var b = storage.store(new ByteArrayInputStream(ImageFixtures.PNG));
        assertThat(a.key()).matches("[0-9a-f]{32}").isNotEqualTo(b.key());
        assertThat(a.contentType()).isEqualTo("image/png");
        try (var input = storage.open(a.key())) { assertThat(input.readAllBytes()).isEqualTo(ImageFixtures.PNG); }
        assertThat(a.sizeBytes()).isEqualTo(ImageFixtures.PNG.length);
        storage.delete(a.key());
        assertThat(Files.exists(root.resolve(b.key()))).isTrue();
    }
    @Test void deletesPartialFileOnStreamFailure() throws Exception {
        var storage = new LocalImageStorage(new ImageStorageProperties(root));
        var broken = new InputStream() {
            int position;
            @Override public int read() throws IOException {
                if (position >= 9000) throw new IOException("test-only-stream-failure");
                int current = position++;
                return current < ImageFixtures.PNG.length ? ImageFixtures.PNG[current] & 255 : 0;
            }
        };
        assertThatThrownBy(() -> storage.store(broken)).isInstanceOf(IOException.class);
        try (var files = Files.list(root)) { assertThat(files.count()).isZero(); }
    }
    @Test void rejectsOversizeBytesEvenWhenDeclaredSizeWasUntrusted() throws Exception {
        var storage = new LocalImageStorage(new ImageStorageProperties(root));
        byte[] huge = java.util.Arrays.copyOf(ImageFixtures.PNG, 10000001);
        assertThatThrownBy(() -> storage.store(new ByteArrayInputStream(huge))).isInstanceOf(ApiException.class);
        try (var files = Files.list(root)) { assertThat(files.count()).isZero(); }
    }
    @Test void refusesTraversalAndInvalidKeys() throws Exception {
        var storage = new LocalImageStorage(new ImageStorageProperties(root));
        for (String key : new String[]{"../secret", "..\\secret", "/absolute", "A".repeat(32), "a".repeat(31)})
            assertThatThrownBy(() -> storage.open(key)).isInstanceOf(ApiException.class);
    }
}
