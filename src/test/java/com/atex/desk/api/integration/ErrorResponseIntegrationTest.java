package com.atex.desk.api.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ErrorResponseIntegrationTest extends BaseIntegrationTest {

    private String token;

    @BeforeEach
    void loginBeforeEach() {
        token = loginSysadmin();
    }

    @Test
    void notFound_matchesReferenceFormat() throws Exception {
        HttpResponse<String> response = rawGet(
            "/content/contentid/onecms:nonexistent:v1", token);

        assertEquals(404, response.statusCode());
        String body = response.body();
        // Reference format: {"extraInfo":{},"statusCode":40400,"message":"..."}
        assertTrue(body.contains("statusCode"), "should have statusCode field");
        assertTrue(body.contains("40400"), "statusCode should be 40400");
        assertTrue(body.contains("message"), "should have message field");
        assertTrue(body.contains("extraInfo"), "should have extraInfo field");
    }

    @Test
    void badRequest_matchesReferenceFormat() throws Exception {
        Map<String, Object> created = createContent(token, articleBody("BadReq Test", "Lead", "Body"));
        String unversionedId = extractId(created);

        // PUT without If-Match
        HttpResponse<String> response = rawPut(
            "/content/contentid/" + unversionedId,
            toJson(articleBody("Updated", "Lead", "Body")),
            token, null);

        assertEquals(400, response.statusCode());
        String body = response.body();
        assertTrue(body.contains("statusCode"), "should have statusCode field");
        assertTrue(body.contains("40000"), "statusCode should be 40000");
        assertTrue(body.contains("message"), "should have message field");
    }

    @Test
    void conflict_matchesReferenceFormat() throws Exception {
        Map<String, Object> created = createContent(token, articleBody("Conflict Test", "Lead", "Body"));
        String unversionedId = extractId(created);
        String v1 = extractVersion(created);

        // First update
        restClient.put()
            .uri("/content/contentid/{id}", unversionedId)
            .headers(h -> {
                h.addAll(authHeaders(token));
                h.set("If-Match", "\"" + v1 + "\"");
            })
            .body(articleBody("Updated", "Lead", "Body"))
            .retrieve()
            .body(Map.class);

        // Stale update
        HttpResponse<String> response = rawPut(
            "/content/contentid/" + unversionedId,
            toJson(articleBody("Stale Update", "Lead", "Body")),
            token, "\"" + v1 + "\"");

        assertEquals(409, response.statusCode());
        String body = response.body();
        assertTrue(body.contains("statusCode"), "should have statusCode field");
        assertTrue(body.contains("40900"), "statusCode should be 40900");
    }

    @Test
    void loginError_matchesReferenceFormat() throws Exception {
        HttpResponse<String> response = rawPost(
            "/security/token",
            "{\"username\":\"sysadmin\",\"password\":\"wrong\"}",
            null);

        assertEquals(401, response.statusCode());
        String body = response.body();
        assertTrue(body.contains("statusCode"), "should have statusCode field");
        assertTrue(body.contains("40100"), "statusCode should be 40100");
    }
}
