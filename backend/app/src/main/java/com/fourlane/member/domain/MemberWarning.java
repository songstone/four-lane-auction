package com.fourlane.member.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "member_warning")
public class MemberWarning {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @Column(name = "trade_id", nullable = false) private Long tradeId;
    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(3)") private Instant createdAt;
    protected MemberWarning() {}
}
