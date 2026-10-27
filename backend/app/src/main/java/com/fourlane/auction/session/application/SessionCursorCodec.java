package com.fourlane.auction.session.application;

import com.fourlane.auction.session.domain.SessionStatus;
import com.fourlane.global.error.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class SessionCursorCodec {
    public record Position(Instant time, long id) {}

    public String encode(SessionStatus status, Instant time, long id) {
        String value = "1|" + status.name() + "|" + (time == null ? "" : time) + "|" + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public Position decode(String cursor, SessionStatus status) {
        if (cursor == null) {
            return null;
        }
        try {
            if (cursor.isBlank() || cursor.length() > 512) {
                throw new IllegalArgumentException();
            }
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
                    .split("\\|", -1);
            if (parts.length != 4 || !parts[0].equals("1") || !parts[1].equals(status.name())) {
                throw new IllegalArgumentException();
            }
            Instant time = parts[2].isEmpty() ? null : Instant.parse(parts[2]);
            long id = Long.parseLong(parts[3]);
            if (id <= 0 || id > 9_007_199_254_740_991L
                    || (time == null && status == SessionStatus.SCHEDULED)
                    || (time != null && time.getNano() % 1_000_000 != 0)
                    || !encode(status, time, id).equals(cursor)) {
                throw new IllegalArgumentException();
            }
            return new Position(time, id);
        } catch (RuntimeException exception) {
            throw ApiException.invalidRequest("cursor", "INVALID_FORMAT");
        }
    }
}
