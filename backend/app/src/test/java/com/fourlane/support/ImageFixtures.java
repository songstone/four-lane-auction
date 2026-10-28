package com.fourlane.support;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ImageFixtures {
    private ImageFixtures() {}
    public static final byte[] PNG = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j/a8AAAAASUVORK5CYII=");
    public static final byte[] WEBP = Base64.getDecoder().decode("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEAAUAmJaQAA3AA/v89WAAAAA==");
    public static byte[] jpeg() throws IOException {
        var output = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB), "jpeg", output);
        return output.toByteArray();
    }
    public static final String BOUNDARY = "FourLaneTestBoundary";
    public static byte[] multipart(Map<String, String> fields, List<byte[]> images) throws IOException {
        var output = new ByteArrayOutputStream();
        for (var entry : fields.entrySet()) {
            output.write(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"" + entry.getKey()
                    + "\"\r\n\r\n" + entry.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        for (var bytes : images) {
            // Deliberately identical file names and an untrusted MIME: storage must use bytes and random keys.
            output.write(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"images\"; filename=\"same.jpg\"\r\n"
                    + "Content-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            output.write(bytes);
            output.write("\r\n".getBytes(StandardCharsets.US_ASCII));
        }
        output.write(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.US_ASCII));
        return output.toByteArray();
    }
}
