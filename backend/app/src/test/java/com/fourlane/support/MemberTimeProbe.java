package com.fourlane.support;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

// Test-only mapping of an existing V1 table; no product entity or extra DDL.
@Entity
@Table(name = "member")
public class MemberTimeProbe {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 30)
    private String nickname;

    @Column(nullable = false, length = 10)
    private String role;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(3)")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(3)")
    private Instant updatedAt;

    protected MemberTimeProbe() {
    }

    public MemberTimeProbe(Instant time) {
        email = UUID.randomUUID() + "@example.invalid";
        passwordHash = "test-only-unused-password-hash";
        nickname = "시간 검증";
        role = "USER";
        createdAt = time;
        updatedAt = time;
    }

    public Long id() {
        return id;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
