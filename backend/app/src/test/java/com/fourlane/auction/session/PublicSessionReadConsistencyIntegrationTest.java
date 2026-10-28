package com.fourlane.auction.session;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import com.fourlane.auction.session.application.PublicSessionQueryService;
import com.fourlane.auction.session.application.PublicSessionView;
import com.fourlane.auction.session.infra.JpaPublicSessionCatalog;
import com.fourlane.global.error.ApiException;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

class PublicSessionReadConsistencyIntegrationTest extends SessionLineupTestSupport {
    @Autowired PublicSessionQueryService queries;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean JpaPublicSessionCatalog catalog;
    record Gate(CountDownLatch read,CountDownLatch committed) {}
    @Test void twoHundredReplacementsCannotTearHeaderLineupAndPersonalLots() throws Exception {
        long id=create(), a=item("일관성",true);
        lineup.replace(id,1,List.of(a));
        var gate=new AtomicReference<Gate>();
        doAnswer(call -> {
            Object header=call.callRealMethod();
            var current=gate.get();
            if(current!=null) {
                current.read().countDown();
                if(!current.committed().await(15,TimeUnit.SECONDS))throw new AssertionError("Writer did not commit");
            }
            return header;
        }).when(catalog).header(anyLong());
        try(var executor=Executors.newSingleThreadExecutor()) {
            for(int n=0;n<200;n++) {
                long old=jdbc.queryForObject("SELECT id FROM lot WHERE lane_id=?",Long.class,laneId(id,1));
                var current=new Gate(new CountDownLatch(1),new CountDownLatch(1));
                gate.set(current);
                Future<PublicSessionView> reading=executor.submit(()->queries.session(id,seller));
                try {
                    assertThat(current.read().await(15,TimeUnit.SECONDS)).isTrue();
                    lineup.replace(id,1,List.of(a)); // Separate committed TX while the reader's snapshot is held.
                } finally { current.committed().countDown(); }
                var result=reading.get(15,TimeUnit.SECONDS);
                assertThat(result.myLotIds()).containsExactly(old);
                assertThat(result.rows().stream().filter(r->r.getLotId()!=null).map(r->r.getLotId()).toList()).containsExactly(old);
                gate.set(null);
                assertThat(queries.session(id,seller).myLotIds()).doesNotContain(old).hasSize(1);
            }
        }
    }
    @Test void incompatibleOuterTransactionIsRejected() {
        var tx=new TransactionTemplate(transactions);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        tx.setReadOnly(true);
        assertThatThrownBy(()->tx.execute(s->queries.session(1,null))).isInstanceOf(ApiException.class);
    }
}
