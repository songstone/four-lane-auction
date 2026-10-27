package com.fourlane.member.application;

import com.fourlane.member.domain.MemberRole;

public record MemberView(long memberId, String email, String nickname, MemberRole role,
                         boolean bidSuspended, long warningCount) {}
