package com.fourlane.item;

import static org.assertj.core.api.Assertions.*;
import com.fourlane.global.error.ApiException;
import com.fourlane.item.infra.ImageTypeDetector;
import com.fourlane.support.ImageFixtures;
import org.junit.jupiter.api.Test;

class ImageTypeDetectorTest {
    @Test void detectsAllThreeFormatsFromBytes() throws Exception {
        assertThat(ImageTypeDetector.detect(ImageFixtures.PNG)).isEqualTo("image/png");
        assertThat(ImageTypeDetector.detect(ImageFixtures.jpeg())).isEqualTo("image/jpeg");
        assertThat(ImageTypeDetector.detect(ImageFixtures.WEBP)).isEqualTo("image/webp");
    }
    @Test void rejectsTextEmptyTruncatedAndOtherRiffTypes() {
        for (byte[] bytes : new byte[][] {new byte[0], new byte[]{(byte) 0xff, (byte) 0xd8},
                "text pretending to be jpg".getBytes(), "RIFF0000WAVEfmt ".getBytes(), "RIFF0000WEBPxxxx".getBytes()})
            assertThatThrownBy(() -> ImageTypeDetector.detect(bytes)).isInstanceOf(ApiException.class);
    }
}
