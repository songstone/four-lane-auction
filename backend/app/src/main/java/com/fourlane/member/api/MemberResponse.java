package com.fourlane.member.api;

import com.fourlane.member.application.MemberView;
import com.fourlane.member.domain.MemberRole;
import io.swagger.v3.oas.annotations.media.Schema;

public record MemberResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long memberId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String nickname,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) MemberRole role,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean bidSuspended,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long warningCount) {
    public static MemberResponse from(MemberView view) {
        return new MemberResponse(view.memberId(), view.email(), view.nickname(), view.role(),
                view.bidSuspended(), view.warningCount());
    }
}
