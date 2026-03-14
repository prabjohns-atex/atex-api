package com.atex.desk.api.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for content operations: trash, untrash, archive, clearengage.
 */
class ContentOpsIntegrationTest extends BaseIntegrationTest {

    private String token;

    @BeforeEach
    void loginBeforeEach() {
        token = loginSysadmin();
    }

    // ---- Trash / Untrash ----

    @Test
    void trashContent_returns200() throws Exception {
        Map<String, Object> created = createContent(token, articleBody("Trash Test", "lead", "body"));
        String contentId = extractId(created);

        HttpResponse<String> resp = rawPost("/dam/content/trash/" + contentId, null, token);
        assertEquals(200, resp.statusCode(), "trash should return 200: " + resp.body());
    }

    @Test
    void trashContent_setsSpikedState() throws Exception {
        Map<String, Object> created = createContent(token, articleBody("Spike Test", "lead", "body"));
        String contentId = extractId(created);

        rawPost("/dam/content/trash/" + contentId, null, token);

        // Fetch content and check that itemState aspect is SPIKED
        HttpResponse<String> getResp = rawGet("/content/contentid/" + contentId, token);
        // Follow the redirect
        if (getResp.statusCode() == 303) {
            String location = getResp.headers().firstValue("location").orElseThrow();
            getResp = rawGet(location.replace(baseUrl(), ""), token);
        }
        assertEquals(200, getResp.statusCode());
        assertTrue(getResp.body().contains("SPIKED"), "content should have SPIKED state: " + getResp.body());
    }

    @Test
    void untrashContent_restoresState() throws Exception {
        Map<String, Object> created = createContent(token, articleBody("Untrash Test", "lead", "body"));
        String contentId = extractId(created);

        // Trash first
        HttpResponse<String> trashResp = rawPost("/dam/content/trash/" + contentId, null, token);
        assertEquals(200, trashResp.statusCode(), "trash should succeed");

        // Untrash
        HttpResponse<String> untrashResp = rawPost("/dam/content/untrash/" + contentId, null, token);
        assertEquals(200, untrashResp.statusCode(), "untrash should return 200: " + untrashResp.body());

        // Fetch and check state is PRODUCTION
        HttpResponse<String> getResp = rawGet("/content/contentid/" + contentId, token);
        if (getResp.statusCode() == 303) {
            String location = getResp.headers().firstValue("location").orElseThrow();
            getResp = rawGet(location.replace(baseUrl(), ""), token);
        }
        assertEquals(200, getResp.statusCode());
        assertTrue(getResp.body().contains("PRODUCTION"), "content should have PRODUCTION state: " + getResp.body());
    }

    @Test
    void trashContent_requiresAuth() throws Exception {
        Map<String, Object> created = createContent(token, articleBody("Auth Test", "lead", "body"));
        String contentId = extractId(created);

        HttpResponse<String> resp = rawPost("/dam/content/trash/" + contentId, null, null);
        assertEquals(401, resp.statusCode());
    }

    @Test
    void trashContent_notFound() throws Exception {
        HttpResponse<String> resp = rawPost("/dam/content/trash/onecms:nonexistent", null, token);
        assertTrue(resp.statusCode() >= 400, "should fail for missing content");
    }

    // ---- Archive ----

    @Test
    void archiveContent_returns200() throws Exception {
        Map<String, Object> created = createContent(token, articleBody("Archive Test", "lead", "body"));
        String contentId = extractId(created);

        HttpResponse<String> resp = rawPost("/dam/content/archive/" + contentId, null, token);
        assertEquals(200, resp.statusCode(), "archive should return 200: " + resp.body());
    }

    @Test
    void archiveContent_setsPartition() throws Exception {
        Map<String, Object> created = createContent(token, articleBody("Archive Partition", "lead", "body"));
        String contentId = extractId(created);

        rawPost("/dam/content/archive/" + contentId, null, token);

        // Fetch content and check partition metadata
        HttpResponse<String> getResp = rawGet("/content/contentid/" + contentId, token);
        if (getResp.statusCode() == 303) {
            String location = getResp.headers().firstValue("location").orElseThrow();
            getResp = rawGet(location.replace(baseUrl(), ""), token);
        }
        assertEquals(200, getResp.statusCode());
        assertTrue(getResp.body().contains("archive"), "content should have archive partition: " + getResp.body());
    }

    @Test
    void archiveContent_requiresAuth() throws Exception {
        Map<String, Object> created = createContent(token, articleBody("Auth Archive", "lead", "body"));
        String contentId = extractId(created);

        HttpResponse<String> resp = rawPost("/dam/content/archive/" + contentId, null, null);
        assertEquals(401, resp.statusCode());
    }

    // ---- clearengage POST ----

    @Test
    void clearEngagePost_returns200() throws Exception {
        Map<String, Object> created = createContent(token, articleBody("ClearEngage Test", "lead", "body"));
        String contentId = extractId(created);

        HttpResponse<String> resp = rawPost("/dam/content/clearengage/" + contentId, null, token);
        assertEquals(200, resp.statusCode(), "clearengage POST should return 200: " + resp.body());
    }

    @Test
    void clearEngagePost_requiresAuth() throws Exception {
        Map<String, Object> created = createContent(token, articleBody("ClearEngage Auth", "lead", "body"));
        String contentId = extractId(created);

        HttpResponse<String> resp = rawPost("/dam/content/clearengage/" + contentId, null, null);
        assertEquals(401, resp.statusCode());
    }
}
