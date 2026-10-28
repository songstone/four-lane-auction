package com.fourlane.item.application;

import com.fourlane.global.error.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class AdminItemCursorCodec {
    public record Position(Instant time, long id) {}
    public String encode(String stage, Instant time, long id) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(("1|" + stage + "|" + time + "|" + id).getBytes(StandardCharsets.UTF_8));
    }
    public Position decode(String cursor, String stage) {
        if (cursor == null) return null;
        try {
            if (cursor.isBlank() || cursor.length() > 512) throw new IllegalArgumentException();
            var parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length != 4 || !parts[0].equals("1") || !parts[1].equals(stage)) throw new IllegalArgumentException();
            var time = Instant.parse(parts[2]); long id = Long.parseLong(parts[3]);
            if (id <= 0 || id > 9_007_199_254_740_991L || time.getNano() % 1_000_000 != 0 || !encode(stage, time, id).equals(cursor)) throw new IllegalArgumentException();
            return new Position(time, id);
        } catch (RuntimeException exception) { throw ApiException.invalidRequest("cursor", "INVALID_FORMAT"); }
    }
}
