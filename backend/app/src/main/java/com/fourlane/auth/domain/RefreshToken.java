package com.fourlane.auth.domain;

import jakarta.persistence.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

@Entity
@Table(name = "refresh_token")
public class RefreshToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @Column(name = "token_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)") private String tokenHash;
    @Column(name = "expires_at", nullable = false, columnDefinition = "DATETIME(3)") private Instant expiresAt;
    @Column(name = "revoked_at", columnDefinition = "DATETIME(3)") private Instant revokedAt;
    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(3)") private Instant createdAt;
    protected RefreshToken() {}

    public RefreshToken(long memberId, String raw, Instant expiresAt, Instant now) {
        this.memberId = memberId;
        this.tokenHash = hashOf(raw);
        this.expiresAt = expiresAt;
        this.createdAt = now;
    }

    public static String hashOf(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    public boolean validAt(Instant now) { return revokedAt == null && now.isBefore(expiresAt); }
    public void revoke(Instant now) { if (revokedAt == null) revokedAt = now; }
    public long memberId() { return memberId; }
    public Instant expiresAt() { return expiresAt; }
}
