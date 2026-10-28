package com.fourlane.auction.session;
import static org.assertj.core.api.Assertions.*;
import com.fourlane.auction.session.application.AdminSessionService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
class SessionMutationFailureIntegrationTest extends SessionTestSupport {
    @Autowired AdminSessionService service;@Autowired PlatformTransactionManager transactions;
    @Test void laneInsertFailureRollsBackSessionAndEveryLane()throws Exception{
        jdbc.execute("ALTER TABLE lane ADD CONSTRAINT ck_test_session_lane_failure CHECK(lane_no<>2)");
        try{var r=request("POST","/api/admin/sessions",VALID);assertThat(r.statusCode()).isEqualTo(500);assertThat(count("auction_session")).isZero();assertThat(count("lane")).isZero();}
        finally{jdbc.execute("ALTER TABLE lane DROP CHECK ck_test_session_lane_failure");}
        create();
    }
    @Test void failureBeforeCommitRollsBackCreateAndPatch()throws Exception{
        var input=new AdminSessionService.Input(2,BASE.plusSeconds(60),Map.of());
        assertThatThrownBy(()->new TransactionTemplate(transactions).execute(status->{service.create(input);failCommit();return null;})).isInstanceOf(IllegalStateException.class);
        assertThat(count("auction_session")).isZero();assertThat(count("lane")).isZero();long id=create();var before=jdbc.queryForMap("SELECT * FROM auction_session WHERE id=?",id);
        assertThatThrownBy(()->new TransactionTemplate(transactions).execute(status->{service.patch(id,new AdminSessionService.Input(null,BASE.plusSeconds(120),Map.of("softCloseMs",1234)));failCommit();return null;})).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForMap("SELECT * FROM auction_session WHERE id=?",id)).isEqualTo(before);
    }
    private void failCommit(){TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void beforeCommit(boolean readOnly){throw new IllegalStateException("test beforeCommit failure");}});}
}
