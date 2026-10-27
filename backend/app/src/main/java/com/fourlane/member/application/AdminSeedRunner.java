package com.fourlane.member.application;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("admin-seed")
public class AdminSeedRunner implements ApplicationRunner {
    private final MemberService members;
    private final Clock clock;
    private final String email;
    private final String hash;

    public AdminSeedRunner(MemberService members, Clock clock,
            @Value("${ADMIN_SEED_EMAIL:}") String email, @Value("${ADMIN_SEED_PASSWORD_HASH:}") String hash) {
        this.members = members;
        this.clock = clock;
        this.email = email;
        this.hash = hash;
    }

    @Override
    public void run(ApplicationArguments args) { members.seedAdmin(email, hash, clock.instant()); }
}
