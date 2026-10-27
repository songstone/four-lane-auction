package com.fourlane.member.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Locale;

@Entity
@Table(name = "member")
public class Member {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 255) private String email;
    @Column(name = "password_hash", nullable = false, length = 100) private String passwordHash;
    @Column(nullable = false, length = 30) private String nickname;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private MemberRole role;
    @Column(name = "bid_suspended_at", columnDefinition = "DATETIME(3)") private Instant bidSuspendedAt;
    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(3)") private Instant createdAt;
    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(3)") private Instant updatedAt;

    protected Member() {}

    public Member(String email, String passwordHash, String nickname, MemberRole role, Instant now) {
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.role = role;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static String normalizeEmail(String email) { return email.toLowerCase(Locale.ROOT); }
    public Long id() { return id; }
    public String email() { return email; }
    public String passwordHash() { return passwordHash; }
    public String nickname() { return nickname; }
    public MemberRole role() { return role; }
    public boolean bidSuspended() { return role == MemberRole.USER && bidSuspendedAt != null; }
}
