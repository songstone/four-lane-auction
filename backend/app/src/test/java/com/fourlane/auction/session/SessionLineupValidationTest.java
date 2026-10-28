package com.fourlane.auction.session;

import static org.assertj.core.api.Assertions.*;
import com.fourlane.auction.session.api.SessionLineupRequests;
import com.fourlane.global.error.ApiException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class SessionLineupValidationTest {
    private final JsonMapper json = JsonMapper.builder().build();
    @Test void preservesOrderAndAcceptsEmptyWithoutInventingArrayLimit() {
        assertThat(new SessionLineupRequests.Replace(json.readTree("[3,1,2]")).ids()).containsExactly(3L,1L,2L);
        assertThat(new SessionLineupRequests.Replace(json.readTree("[]")).ids()).isEmpty();
    }
    @Test void validatesStrictJsonTypesBoundsAndDuplicates() {
        for (String raw : new String[]{"null","{}","1","[null]","[\"1\"]","[1.0]","[true]","[0]","[-1]","[9007199254740992]","[9223372036854775808]","[1,1]"}) {
            assertThatThrownBy(() -> new SessionLineupRequests.Replace(json.readTree(raw)).ids()).as(raw).isInstanceOf(ApiException.class);
        }
        assertThatThrownBy(() -> new SessionLineupRequests.Replace(json.readTree("[1,1]")).ids())
            .satisfies(e -> { var f=((ApiException)e).getFields().getFirst(); assertThat(f.field()).isEqualTo("itemIds[1]"); assertThat(f.reason()).isEqualTo("DUPLICATE"); });
        assertThatThrownBy(() -> new SessionLineupRequests.Replace(null).ids()).isInstanceOf(ApiException.class);
    }
}
