package com.fourlane.item;

import static org.assertj.core.api.Assertions.*;
import com.fourlane.support.ImageFixtures;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

class MultipartLimitHttpIntegrationTest extends ItemTestSupport {
    record Part(byte[] header, int size, boolean image) {}
    List<Part> parts(int files, int fileBytes, int padding) {
        var parts = new ArrayList<Part>();
        parts.add(new Part(("--" + ImageFixtures.BOUNDARY + "\r\nContent-Disposition: form-data; name=\"name\"\r\n\r\n한도 검사\r\n"
                + "--" + ImageFixtures.BOUNDARY + "\r\nContent-Disposition: form-data; name=\"startPrice\"\r\n\r\n1000\r\n").getBytes(StandardCharsets.UTF_8), 0, false));
        for (int i = 0; i < files; i++) parts.add(new Part(("--" + ImageFixtures.BOUNDARY
                + "\r\nContent-Disposition: form-data; name=\"images\"; filename=\"same.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.US_ASCII), fileBytes, true));
        if (padding > 0) parts.add(new Part(("--" + ImageFixtures.BOUNDARY
                + "\r\nContent-Disposition: form-data; name=\"padding\"\r\n\r\n").getBytes(StandardCharsets.US_ASCII), padding, false));
        return parts;
    }
    HttpResponse<String> send(int files, int fileBytes, int padding, boolean chunked) throws Exception {
        var parts = parts(files, fileBytes, padding);
        byte[] end = ("--" + ImageFixtures.BOUNDARY + "--\r\n").getBytes(StandardCharsets.US_ASCII);
        long length = parts.stream().mapToLong(part -> part.header().length + part.size() + (part.size() == 0 ? 0 : 2)).sum() + end.length;
        var publisher = HttpRequest.BodyPublishers.ofInputStream(() -> {
            var inputs = new ArrayList<InputStream>();
            for (var part : parts) {
                inputs.add(new ByteArrayInputStream(part.header()));
                if (part.size() > 0) {
                    inputs.add(new InputStream() {
                        int position;
                        @Override public int read() {
                            if (position >= part.size()) return -1;
                            int current = position++;
                            return part.image() && current < ImageFixtures.PNG.length ? ImageFixtures.PNG[current] & 255 : 0;
                        }
                        @Override public int read(byte[] bytes, int offset, int size) {
                            if (position >= part.size()) return -1;
                            int count = Math.min(size, part.size() - position);
                            Arrays.fill(bytes, offset, offset + count, (byte) 0);
                            if (part.image() && position < ImageFixtures.PNG.length)
                                System.arraycopy(ImageFixtures.PNG, position, bytes, offset, Math.min(count, ImageFixtures.PNG.length - position));
                            position += count;
                            return count;
                        }
                    });
                    inputs.add(new ByteArrayInputStream(new byte[]{13, 10}));
                }
            }
            inputs.add(new ByteArrayInputStream(end));
            return new SequenceInputStream(Collections.enumeration(inputs));
        });
        return CLIENT.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/items"))
                .header("Authorization", "Bearer " + token(seller, "USER"))
                .header("Content-Type", "multipart/form-data; boundary=" + ImageFixtures.BOUNDARY)
                .timeout(java.time.Duration.ofSeconds(45))
                .POST(chunked ? publisher : HttpRequest.BodyPublishers.fromPublisher(publisher, length)).build(), HttpResponse.BodyHandlers.ofString());
    }
    void assert413(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/json");
        assertThat(body(response).at("/error/code").asText()).isEqualTo("IMAGE_TOO_LARGE");
        assertThat(response.body()).doesNotContain("Exception", "ProblemDetail", "stackTrace", "timestamp");
        assertThat(count("item")).isZero(); assertThat(count("item_image")).isZero(); assertThat(fileCount()).isZero();
    }
    @Test void servletFileLimitReturnsApiJsonForKnownLength() throws Exception { assert413(send(1, 10000001, 0, false)); }
    @Test void servletFileLimitReturnsApiJsonForChunkedBody() throws Exception { assert413(send(1, 10000001, 0, true)); }
    @Test void servletWholeRequestLimitReturnsApiJson() throws Exception { assert413(send(10, 10000000, 1100000, false)); }
    @Test void acceptsTenFilesAtExactPerFileBoundaryWithinRequestLimit() throws Exception {
        assertThat(send(10, 10000000, 0, false).statusCode()).isEqualTo(201);
        assertThat(count("item_image")).isEqualTo(10); assertThat(fileCount()).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT SUM(size_bytes) FROM item_image", Long.class)).isEqualTo(100000000);
    }
}
