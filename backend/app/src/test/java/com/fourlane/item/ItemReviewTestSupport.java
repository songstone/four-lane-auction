package com.fourlane.item;

import static org.assertj.core.api.Assertions.assertThat;
import com.fourlane.support.ImageFixtures;
import java.net.URI;
import java.net.http.*;
import java.util.*;

abstract class ItemReviewTestSupport extends ItemTestSupport {
    protected long newItem(String name) throws Exception {
        var response = upload(seller, "USER", Map.of("name", name, "startPrice", "50000", "reservePrice", "917431000"), List.of(ImageFixtures.PNG));
        assertThat(response.statusCode()).isEqualTo(201);
        return body(response).path("itemId").asLong();
    }
    protected HttpResponse<String> request(String method, String path, long member, String role, String content) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (role != null) request.header("Authorization", "Bearer " + token(member, role));
        if (content != null) request.header("Content-Type", "application/json");
        return CLIENT.send(request.method(method, content == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(content)).build(), HttpResponse.BodyHandlers.ofString());
    }
    protected HttpResponse<String> review(long item, String action) throws Exception {
        return request("POST", "/api/admin/items/" + item + "/" + action, admin, "ADMIN", action.equals("reject") ? "{\"reason\":\"사진 확인 불가\"}" : null);
    }
    protected HttpResponse<String> list(String stage) throws Exception { return request("GET", "/api/admin/items?stage=" + stage, admin, "ADMIN", null); }
}
