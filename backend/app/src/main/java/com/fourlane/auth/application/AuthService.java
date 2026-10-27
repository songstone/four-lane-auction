package com.fourlane.auth.application;

import com.fourlane.auth.domain.*;
import com.fourlane.global.error.*;
import com.fourlane.global.security.JwtTokenIssuer;
import com.fourlane.member.application.*;
import java.security.SecureRandom;
import java.time.*;
import java.util.Base64;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final MemberService members;
    private final RefreshTokenRepository tokens;
    private final PasswordEncoder passwords;
    private final JwtTokenIssuer issuer;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final String dummyHash;

    public AuthService(MemberService members, RefreshTokenRepository tokens, PasswordEncoder passwords,
            JwtTokenIssuer issuer, Clock clock) {
        this.members = members;
        this.tokens = tokens;
        this.passwords = passwords;
        this.issuer = issuer;
        this.clock = clock;
        this.dummyHash = passwords.encode("unused-login-timing-password");
    }

    @Transactional
    public MemberView signup(String email, String password, String nickname) {
        return members.register(email, passwords.encode(password), nickname, clock.instant());
    }

    @Transactional
    public AuthResult login(String email, String password) {
        var member = members.findForLogin(email);
        boolean matches = passwords.matches(password, member.map(m -> m.passwordHash()).orElse(dummyHash));
        if (member.isEmpty() || !matches) throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        Instant now = clock.instant();
        return issue(members.get(member.get().id()), now, now.plus(Duration.ofDays(7)));
    }

    @Transactional
    public AuthResult refresh(String raw) {
        requireTokenShape(raw);
        RefreshToken previous = tokens.findForUpdate(RefreshToken.hashOf(raw))
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
        // Read AFTER acquiring the row lock: a request can expire while waiting.
        Instant now = clock.instant();
        if (!previous.validAt(now)) throw new ApiException(ErrorCode.UNAUTHENTICATED);
        previous.revoke(now);
        return issue(members.get(previous.memberId()), now, previous.expiresAt());
    }

    @Transactional
    public void logout(String raw) {
        if (raw == null || !raw.matches("[A-Za-z0-9_-]{43}")) return;
        tokens.findForUpdate(RefreshToken.hashOf(raw)).ifPresent(t -> t.revoke(clock.instant()));
    }

    private AuthResult issue(MemberView member, Instant now, Instant refreshExpiresAt) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.saveAndFlush(new RefreshToken(member.memberId(), raw, refreshExpiresAt, now));
        var access = issuer.issue(member.memberId(), member.role().name(), now);
        return new AuthResult(access.value(), access.expiresAt(), raw,
                Duration.between(now, refreshExpiresAt).toSeconds(), member);
    }

    private void requireTokenShape(String raw) {
        if (raw == null || !raw.matches("[A-Za-z0-9_-]{43}")) throw new ApiException(ErrorCode.UNAUTHENTICATED);
    }
}
