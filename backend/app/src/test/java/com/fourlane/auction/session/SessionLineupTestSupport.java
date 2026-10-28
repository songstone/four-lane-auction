package com.fourlane.auction.session;

import static org.assertj.core.api.Assertions.*;
import com.fourlane.auction.session.application.SessionLineupService;
import com.fourlane.support.ImageFixtures;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class SessionLineupTestSupport extends SessionTestSupport {
    @Autowired protected SessionLineupService lineup;
    protected long item(String name, boolean approve) throws Exception {
        var r = upload(seller, "USER", Map.of("name", name, "startPrice", "50000", "reservePrice", "917431000"), List.of(ImageFixtures.PNG));
        assertThat(r.statusCode()).as(r.body()).isEqualTo(201);
        long id = body(r).path("itemId").asLong();
        if (approve) assertThat(request("POST", "/api/admin/items/" + id + "/approve", null).statusCode()).isEqualTo(200);
        return id;
    }
    protected String path(long session, int lane) { return "/api/admin/sessions/" + session + "/lanes/" + lane + "/lots"; }
    protected String ids(long... ids) { return "{\"itemIds\":" + Arrays.toString(ids) + "}"; }
    protected long laneId(long session, int lane) {
        return jdbc.queryForObject("SELECT id FROM lane WHERE session_id=? AND lane_no=?", Long.class, session, lane);
    }
}
