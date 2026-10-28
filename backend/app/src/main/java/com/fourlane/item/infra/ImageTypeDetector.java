package com.fourlane.item.infra;

import com.fourlane.global.error.ApiException;
import com.fourlane.global.error.ErrorCode;
import java.nio.charset.StandardCharsets;

/** Signature identification, not an image decoder or a complete integrity validator. */
public final class ImageTypeDetector {
    private ImageTypeDetector() {}
    public static String detect(byte[] header) {
        if (starts(header, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff})) return "image/jpeg";
        if (starts(header, new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 13, 10, 26, 10})) return "image/png";
        if (header.length >= 16 && text(header, 0, 4).equals("RIFF") && text(header, 8, 4).equals("WEBP")
                && java.util.Set.of("VP8 ", "VP8L", "VP8X").contains(text(header, 12, 4))) return "image/webp";
        throw new ApiException(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
    }
    private static boolean starts(byte[] bytes, byte[] prefix) {
        return bytes.length >= prefix.length && java.util.Arrays.equals(bytes, 0, prefix.length, prefix, 0, prefix.length);
    }
    private static String text(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length, StandardCharsets.US_ASCII);
    }
}
