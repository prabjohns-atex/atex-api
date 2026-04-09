package com.atex.desk.api.integration;

import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for MyTypeResource content CRUD endpoints.
 *
 * POST   /dam/mytype/content                           — create content
 * GET    /dam/mytype/content/contentid/{id}             — get (303 if unversioned, 200 if versioned)
 * PUT    /dam/mytype/content/contentid/{id}             — update content
 * GET    /dam/mytype/configuration/externalid/{id}      — get configuration
 * GET    /dam/mytype/remotePublicationUrl/contentid/{id} — remote publication URL
 */
@TestPropertySource(properties = "desk.config.enabled=true")
class MyTypeContentIntegrationTest extends BaseIntegrationTest {

    private static final Gson GSON = new Gson();
    private String token;

    @BeforeEach
    void loginBeforeEach() {
        token = loginSysadmin();
    }

    // ---- POST /dam/mytype/content ----

    @Test
    @SuppressWarnings("unchecked")
    void createContent_returnsCreated() throws Exception {
        String body = GSON.toJson(articlePayload("Create Test", "lead text", "body text"));
        HttpResponse<String> resp = rawPost("/dam/mytype/content", body, token);
        assertEquals(201, resp.statusCode(), "Should return 201 Created, body: " + resp.body());

        Map<String, Object> result = GSON.fromJson(resp.body(), Map.class);
        assertNotNull(result.get("id"), "Response should have 'id'");
        assertNotNull(result.get("version"), "Response should have 'version'");
    }

    @Test
    void createContent_noAuth_returns401() throws Exception {
        String body = GSON.toJson(articlePayload("No Auth", "lead", "body"));
        HttpResponse<String> resp = rawPost("/dam/mytype/content", body, null);
        assertEquals(401, resp.statusCode());
    }

    // ---- GET /dam/mytype/content/contentid/{id} ----

    @Test
    @SuppressWarnings("unchecked")
    void getContent_unversioned_returns303() throws Exception {
        // Create content first
        String body = GSON.toJson(articlePayload("Get Redirect", "lead", "body"));
        HttpResponse<String> createResp = rawPost("/dam/mytype/content", body, token);
        assertEquals(201, createResp.statusCode(), "Create should succeed: " + createResp.body());

        Map<String, Object> created = GSON.fromJson(createResp.body(), Map.class);
        String unversionedId = (String) created.get("id");
        assertNotNull(unversionedId);

        // GET unversioned should redirect
        HttpResponse<String> resp = rawGet("/dam/mytype/content/contentid/" + unversionedId, token);
        assertEquals(303, resp.statusCode(), "Unversioned GET should return 303");

        // Verify the redirect body contains the expected format
        Map<String, Object> redirectBody = GSON.fromJson(resp.body(), Map.class);
        assertEquals("30300", redirectBody.get("statusCode"));
        assertNotNull(redirectBody.get("location"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getContent_versioned_returns200() throws Exception {
        // Create content first
        String body = GSON.toJson(articlePayload("Get Versioned", "lead", "body"));
        HttpResponse<String> createResp = rawPost("/dam/mytype/content", body, token);
        assertEquals(201, createResp.statusCode(), "Create should succeed: " + createResp.body());

        Map<String, Object> created = GSON.fromJson(createResp.body(), Map.class);
        String versionedId = (String) created.get("version");
        assertNotNull(versionedId);

        // GET versioned should return 200
        HttpResponse<String> resp = rawGet("/dam/mytype/content/contentid/" + versionedId, token);
        assertEquals(200, resp.statusCode(), "Versioned GET should return 200, body: " + resp.body());

        Map<String, Object> result = GSON.fromJson(resp.body(), Map.class);
        assertNotNull(result.get("id"), "Response should have 'id'");
    }

    @Test
    void getContent_notFound_returns404() throws Exception {
        HttpResponse<String> resp = rawGet("/dam/mytype/content/contentid/onecms:nonexistent", token);
        assertEquals(404, resp.statusCode());
    }

    @Test
    void getContent_noAuth_returns401() throws Exception {
        HttpResponse<String> resp = rawGet("/dam/mytype/content/contentid/onecms:fake", null);
        assertEquals(401, resp.statusCode());
    }

    // ---- PUT /dam/mytype/content/contentid/{id} ----

    @Test
    @SuppressWarnings("unchecked")
    void updateContent_succeeds() throws Exception {
        // Create content first
        String createBody = GSON.toJson(articlePayload("Update Test", "lead", "body"));
        HttpResponse<String> createResp = rawPost("/dam/mytype/content", createBody, token);
        assertEquals(201, createResp.statusCode(), "Create should succeed: " + createResp.body());

        Map<String, Object> created = GSON.fromJson(createResp.body(), Map.class);
        String unversionedId = (String) created.get("id");

        // Update
        String updateBody = GSON.toJson(articlePayload("Updated Headline", "updated lead", "updated body"));
        HttpResponse<String> resp = rawPut("/dam/mytype/content/contentid/" + unversionedId,
                updateBody, token, null);
        assertEquals(200, resp.statusCode(), "Update should return 200, body: " + resp.body());

        Map<String, Object> result = GSON.fromJson(resp.body(), Map.class);
        assertNotNull(result.get("id"), "Response should have 'id'");
    }

    @Test
    void updateContent_versionedId_returns400() throws Exception {
        // Create content first
        String createBody = GSON.toJson(articlePayload("Versioned Update", "lead", "body"));
        HttpResponse<String> createResp = rawPost("/dam/mytype/content", createBody, token);
        assertEquals(201, createResp.statusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> created = GSON.fromJson(createResp.body(), Map.class);
        String versionedId = (String) created.get("version");

        // Update with versioned ID should fail
        String updateBody = GSON.toJson(articlePayload("Updated", "lead", "body"));
        HttpResponse<String> resp = rawPut("/dam/mytype/content/contentid/" + versionedId,
                updateBody, token, null);
        assertEquals(400, resp.statusCode(), "Versioned update should return 400");
    }

    @Test
    void updateContent_noAuth_returns401() throws Exception {
        String updateBody = GSON.toJson(articlePayload("No Auth", "lead", "body"));
        HttpResponse<String> resp = rawPut("/dam/mytype/content/contentid/onecms:fake",
                updateBody, null, null);
        assertEquals(401, resp.statusCode());
    }

    // ---- GET /dam/mytype/configuration/externalid/{id} ----

    @Test
    @SuppressWarnings("unchecked")
    void getConfiguration_existingConfig_returns200() throws Exception {
        // mytype.general.permissions is loaded from config defaults
        HttpResponse<String> resp = rawGet(
                "/dam/mytype/configuration/externalid/mytype.general.permissions", token);
        assertEquals(200, resp.statusCode(), "Config should exist, body: " + resp.body());

        Map<String, Object> config = GSON.fromJson(resp.body(), Map.class);
        assertNotNull(config, "Config response should be valid JSON");
    }

    @Test
    void getConfiguration_notFound_returns404() throws Exception {
        HttpResponse<String> resp = rawGet(
                "/dam/mytype/configuration/externalid/nonexistent.config.id", token);
        assertEquals(404, resp.statusCode());
    }

    @Test
    void getConfiguration_noAuth_returns401() throws Exception {
        HttpResponse<String> resp = rawGet(
                "/dam/mytype/configuration/externalid/mytype.general.permissions", null);
        assertEquals(401, resp.statusCode());
    }

    // ---- GET /dam/mytype/remotePublicationUrl/contentid/{id} ----

    @Test
    void remotePublicationUrl_notPublished_returnsError() throws Exception {
        // Create content
        String body = GSON.toJson(articlePayload("Pub URL Test", "lead", "body"));
        HttpResponse<String> createResp = rawPost("/dam/mytype/content", body, token);
        assertEquals(201, createResp.statusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> created = GSON.fromJson(createResp.body(), Map.class);
        String unversionedId = (String) created.get("id");

        // Getting remote publication URL for unpublished content should fail
        HttpResponse<String> resp = rawGet(
                "/dam/mytype/remotePublicationUrl/contentid/" + unversionedId, token);
        // Should return 501 (not implemented / no engagement) or 500
        assertTrue(resp.statusCode() >= 400,
                "Remote pub URL for unpublished content should fail, got: " + resp.statusCode());
    }

    @Test
    void remotePublicationUrl_noAuth_returns401() throws Exception {
        HttpResponse<String> resp = rawGet(
                "/dam/mytype/remotePublicationUrl/contentid/onecms:fake", null);
        assertEquals(401, resp.statusCode());
    }

    // ---- helpers ----

    private Map<String, Object> articlePayload(String headline, String lead, String body) {
        Map<String, Object> contentData = new LinkedHashMap<>();
        contentData.put("_type", "atex.onecms.article");
        contentData.put("headline", Map.of("text", headline));
        contentData.put("lead", Map.of("text", lead));
        contentData.put("body", Map.of("text", body));

        Map<String, Object> aspectData = new LinkedHashMap<>();
        aspectData.put("data", contentData);

        Map<String, Object> aspects = new LinkedHashMap<>();
        aspects.put("contentData", aspectData);

        return Map.of("aspects", aspects);
    }
}
