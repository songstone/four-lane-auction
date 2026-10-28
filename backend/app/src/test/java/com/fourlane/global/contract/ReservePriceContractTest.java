package com.fourlane.global.contract;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import com.fourlane.auth.application.AuthService;
import com.fourlane.item.ItemTestSupport;
import com.fourlane.support.ImageFixtures;
import com.fourlane.support.contract.*;
import jakarta.servlet.http.Cookie;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

class ReservePriceContractTest extends ItemTestSupport {
    static final long MARKER = 917431000;
    static final String PASSWORD = "contract-test-only-password";
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;
    @Autowired PasswordEncoder passwords;
    @Autowired AuthService auth;
    @Test void allImplementedRestResponsesAreRegisteredAndHideReserveFieldsAndValues() throws Exception {
        jdbc.update("UPDATE member SET password_hash=? WHERE id=?", passwords.encode(PASSWORD), seller);
        var item = upload(seller, "USER", Map.of("name", "비공개 검사", "startPrice", "50000", "reservePrice", Long.toString(MARKER)), List.of(ImageFixtures.PNG));
        assertThat(item.statusCode()).isEqualTo(201);
        assertThat(jdbc.queryForObject("SELECT reserve_price FROM item", Long.class)).isEqualTo(MARKER);
        String key = jdbc.queryForObject("SELECT storage_key FROM item_image", String.class);
        String owner = "Bearer " + token(seller, "USER");
        var catalog = new ResponseContractCatalog();
        catalog.rest("POST", "/api/auth/signup", "ANONYMOUS", 201, () -> mvc.perform(post("/api/auth/signup").contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", "catalog-new@example.com", "password", PASSWORD, "nickname", "계약검사")))).andReturn());
        catalog.rest("POST", "/api/auth/login", "ANONYMOUS", 200, () -> mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", "seller@example.com", "password", PASSWORD)))).andReturn());
        catalog.rest("POST", "/api/auth/refresh", "OWNER", 200, () -> mvc.perform(post("/api/auth/refresh")
                .cookie(new Cookie("refreshToken", auth.login("seller@example.com", PASSWORD).refreshToken()))).andReturn());
        catalog.rest("POST", "/api/auth/logout", "OWNER", 204, () -> mvc.perform(post("/api/auth/logout")).andReturn());
        catalog.rest("GET", "/api/me", "OWNER", 200, () -> mvc.perform(get("/api/me").header("Authorization", owner)).andReturn());
        for (String audience : List.of("ANONYMOUS", "OTHER_USER", "ADMIN")) catalog.rest("GET", "/api/sessions", audience, 200, () -> {
            var request = get("/api/sessions").param("status", "SCHEDULED");
            if (!audience.equals("ANONYMOUS")) request.header("Authorization", "Bearer " + token(audience.equals("ADMIN") ? admin : other, audience.equals("ADMIN") ? "ADMIN" : "USER"));
            return mvc.perform(request).andReturn();
        });
        catalog.rest("POST", "/api/items", "OWNER", 201, () -> mvc.perform(multipart("/api/items")
                .file(new MockMultipartFile("images", "same.jpg", "image/jpeg", ImageFixtures.PNG))
                .param("name", "등록 응답 검사").param("startPrice", "50000").param("reservePrice", Long.toString(MARKER)).header("Authorization", owner)).andReturn());
        catalog.rest("POST", "/api/items", "ANONYMOUS", 401, () -> mvc.perform(multipart("/api/items")).andReturn());
        catalog.rest("POST", "/api/items", "ADMIN", 403, () -> mvc.perform(multipart("/api/items").header("Authorization", "Bearer " + token(admin, "ADMIN"))).andReturn());
        catalog.rest("POST", "/api/items", "OWNER_INVALID", 400, () -> mvc.perform(multipart("/api/items").param("startPrice", "12345").header("Authorization", owner)).andReturn());
        catalog.rest("GET", "/api/me/items", "OWNER", 200, () -> mvc.perform(get("/api/me/items").header("Authorization", owner)).andReturn());
        catalog.rest("GET", "/api/me/items", "OTHER_USER", 200, () -> mvc.perform(get("/api/me/items").header("Authorization", "Bearer " + token(other, "USER"))).andReturn());
        catalog.binary("GET", "/api/images/{storageKey}", "ANONYMOUS", () -> mvc.perform(get("/api/images/" + key)).andReturn());
        catalog.rest("GET", "/api/images/{storageKey}", "ANONYMOUS_MISSING", 404, () -> mvc.perform(get("/api/images/" + "a".repeat(32))).andReturn());
        String administrator = "Bearer " + token(admin, "ADMIN");
        long reviewId = body(item).get("itemId").asLong();
        String reviewPath = "/api/admin/items/" + reviewId;
        var secondItem = upload(seller, "USER", Map.of("name", "반려 응답 검사", "startPrice", "50000", "reservePrice", Long.toString(MARKER)), List.of(ImageFixtures.PNG));
        assertThat(secondItem.statusCode()).isEqualTo(201);
        long rejectedId = body(secondItem).path("itemId").asLong();
        catalog.rest("GET", "/api/admin/items", "ADMIN", 200, () -> mvc.perform(get("/api/admin/items").param("stage", "PENDING_REVIEW").header("Authorization", administrator)).andReturn());
        catalog.adminItemDetail(MARKER, () -> mvc.perform(get(reviewPath).header("Authorization", administrator)).andReturn());
        catalog.rest("GET", "/api/admin/items/{itemId}", "USER", 403, () -> mvc.perform(get(reviewPath).header("Authorization", owner)).andReturn());
        catalog.rest("GET", "/api/admin/items/{itemId}", "ADMIN_MISSING", 404, () -> mvc.perform(get("/api/admin/items/9000000000").header("Authorization", administrator)).andReturn());
        catalog.rest("POST", "/api/admin/items/{itemId}/approve", "ADMIN", 200, () -> mvc.perform(post(reviewPath + "/approve").header("Authorization", administrator)).andReturn());
        catalog.rest("POST", "/api/admin/items/{itemId}/approve", "USER", 403, () -> mvc.perform(post(reviewPath + "/approve").header("Authorization", owner)).andReturn());
        catalog.rest("POST", "/api/admin/items/{itemId}/reject", "ADMIN", 200, () -> mvc.perform(post("/api/admin/items/" + rejectedId + "/reject").header("Authorization", administrator).contentType("application/json").content("{\"reason\":\"확인 불가\"}")).andReturn());
        catalog.rest("POST", "/api/admin/items/{itemId}/approve", "ADMIN_REPEATED", 422, () -> mvc.perform(post(reviewPath + "/approve").header("Authorization", administrator)).andReturn());
        catalog.rest("POST", "/api/admin/items/{itemId}/reject", "ADMIN_REPEATED", 422, () -> mvc.perform(post(reviewPath + "/reject").header("Authorization", administrator).contentType("application/json").content("{\"reason\":\"확인 불가\"}")).andReturn());
        catalog.rest("POST", "/api/admin/items/{itemId}/reject", "USER", 403, () -> mvc.perform(post(reviewPath + "/reject").header("Authorization", owner).contentType("application/json").content("{\"reason\":\"확인 불가\"}")).andReturn());
        catalog.verifyRestCoverage(mappings);
        // No product lane event producer exists in M1-03. Future producers must supply real captures and type coverage.
        catalog.verifyEventCoverage(Set.of());
        catalog.verify(json, Set.of(MARKER));
    }
    @Test void detectorRejectsNestedAliasesNumericAndTextLeaksButAllowsBadge() {
        for (String leaked : List.of("{\"nested\":[{\"reservePrice\":null}]}", "{\"reserve_price\":0}",
                "{\"value\":917431000}", "{\"value\":\"917,431,000원\"}", "{\"value\":\"917431000\"}"))
            assertThatThrownBy(() -> ReservePriceAssertions.assertHidden(json.readTree(leaked), Set.of(MARKER))).isInstanceOf(AssertionError.class);
        ReservePriceAssertions.assertHidden(json.readTree("{\"reserveNotMet\":true,\"startPrice\":50000}"), Set.of(MARKER));
    }
    @Test void administratorExceptionAllowsOnlyTheExactTopLevelFieldAndExactStoredValue() {
        ReservePriceAssertions.assertAdminItemDetail(json.readTree("{\"reservePrice\":917431000,\"name\":\"상품\"}"), MARKER, Set.of(MARKER));
        for (String rest : List.of("\"nested\":{\"reservePrice\":0}", "\"alias\":917431000", "\"reserve_price\":0"))
            assertThatThrownBy(() -> ReservePriceAssertions.assertAdminItemDetail(json.readTree("{\"reservePrice\":917431000," + rest + "}"), MARKER, Set.of(MARKER))).isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> ReservePriceAssertions.assertAdminItemDetail(json.readTree("{\"reservePrice\":1}"), MARKER, Set.of(MARKER))).isInstanceOf(AssertionError.class);
    }
}
