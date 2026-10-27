package com.fourlane.member.infra;

import com.fourlane.member.domain.MemberWarning;
import com.fourlane.member.domain.MemberQueries;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface MemberQueryRepository extends Repository<MemberWarning, Long>, MemberQueries {
    @Query("select count(w) from MemberWarning w where w.memberId = :memberId")
    long countWarnings(long memberId);
}
