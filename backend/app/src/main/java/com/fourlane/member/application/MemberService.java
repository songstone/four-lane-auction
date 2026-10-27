package com.fourlane.member.application;

import com.fourlane.global.error.ApiException;
import com.fourlane.global.error.ErrorCode;
import com.fourlane.member.domain.*;
import java.time.Instant;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {
    private final MemberRepository members;
    private final MemberQueries queries;

    public MemberService(MemberRepository members, MemberQueries queries) {
        this.members = members;
        this.queries = queries;
    }

    @Transactional
    public MemberView register(String email, String hash, String nickname, Instant now) {
        if (members.findByEmail(Member.normalizeEmail(email)).isPresent()) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        try {
            return view(members.saveAndFlush(new Member(email, hash, nickname, MemberRole.USER, now)));
        } catch (DataIntegrityViolationException collision) {
            // The only registration uniqueness constraint is uk_member_email.
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
    }

    @Transactional(readOnly = true)
    public Optional<Member> findForLogin(String email) {
        return members.findByEmail(Member.normalizeEmail(email));
    }

    @Transactional(readOnly = true)
    public MemberView get(long id) {
        return view(members.findById(id).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED)));
    }

    private MemberView view(Member member) {
        return new MemberView(member.id(), member.email(), member.nickname(), member.role(),
                member.bidSuspended(), member.role() == MemberRole.ADMIN ? 0 : queries.countWarnings(member.id()));
    }

    @Transactional
    public void seedAdmin(String email, String hash, Instant now) {
        if (email == null || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || email.length() > 255
                || hash == null || !hash.matches("\\{bcrypt}\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}")) {
            throw new IllegalArgumentException("ADMIN seed requires an email and a {bcrypt} hash");
        }
        var existing = members.findByEmail(Member.normalizeEmail(email));
        if (existing.isPresent()) {
            if (existing.get().role() != MemberRole.ADMIN) {
                throw new IllegalStateException("ADMIN seed cannot promote an existing USER");
            }
            return;
        }
        members.saveAndFlush(new Member(email, hash, "운영자", MemberRole.ADMIN, now));
    }
}
