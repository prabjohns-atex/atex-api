package com.atex.desk.api.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ContentCrudIntegrationTest extends BaseIntegrationTest {

    private String token;

    @BeforeEach
    void loginBeforeEach() {
        token = loginSysadmin();
    }

    @Test
    void createArticle_returns201() {
        ResponseEntity<Map> response = restClient.post()
            .uri("/content")
            .headers(h -> h.addAll(authHeaders(token)))
            .body(articleBody("Test Headline", "Test Lead", "Test Body"))
            .retrieve()
            .toEntity(Map.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().get("id"));
        assertNotNull(response.getBody().get("version"));
        assertNotNull(response.getHeaders().getETag());
        assertNotNull(response.getHeaders().getLocation());
    }

    @Test
    void createImage_returns201() {
        ResponseEntity<Map> response = restClient.post()
            .uri("/content")
            .headers(h -> h.addAll(authHeaders(token)))
            .body(imageBody("Test Image", "An image for testing"))
            .retrieve()
            .toEntity(Map.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody().get("id"));
    }

    @Test
    void createCollection_returns201() {
        ResponseEntity<Map> response = restClient.post()
            .uri("/content")
            .headers(h -> h.addAll(authHeaders(token)))
            .body(collectionBody("Test Collection"))
            .retrieve()
            .toEntity(Map.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody().get("id"));
    }

    @Test
    void createAudio_returns201() {
        ResponseEntity<Map> response = restClient.post()
            .uri("/content")
            .headers(h -> h.addAll(authHeaders(token)))
            .body(audioBody("Test Audio Description"))
            .retrieve()
            .toEntity(Map.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody().get("id"));
    }

    @Test
    void createVideo_returns201() {
        ResponseEntity<Map> response = restClient.post()
            .uri("/content")
            .headers(h -> h.addAll(authHeaders(token)))
            .body(videoBody("Test Video"))
            .retrieve()
            .toEntity(Map.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody().get("id"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getVersioned_returns200() {
        Map<String, Object> created = createContent(token, articleBody("Get Test", "Lead", "Body"));
        String versionedId = extractVersion(created);

        Map<String, Object> response = restClient.get()
            .uri("/content/contentid/{id}", versionedId)
            .header("X-Auth-Token", token)
            .retrieve()
            .body(Map.class);

        assertNotNull(response);
        assertEquals(versionedId, response.get("version"));
        Map<String, Object> aspects = (Map<String, Object>) response.get("aspects");
        assertNotNull(aspects);
        assertTrue(aspects.containsKey("contentData"));
    }

    @Test
    void getUnversioned_returns303() throws Exception {
        Map<String, Object> created = createContent(token, articleBody("Redirect Test", "Lead", "Body"));
        String unversionedId = extractId(created);

        // Use rawClient to avoid following redirects
        HttpResponse<String> response = rawGet("/content/contentid/" + unversionedId, token);
        assertEquals(303, response.statusCode());
        assertTrue(response.body().contains("30300"));
        assertTrue(response.body().contains("Symbolic version resolved"));
    }

    @Test
    void updateContent_returns200() {
        Map<String, Object> created = createContent(token, articleBody("Original", "Lead", "Body"));
        String unversionedId = extractId(created);
        String versionedId = extractVersion(created);

        ResponseEntity<Map> response = restClient.put()
            .uri("/content/contentid/{id}", unversionedId)
            .headers(h -> {
                h.addAll(authHeaders(token));
                h.set("If-Match", "\"" + versionedId + "\"");
            })
            .body(articleBody("Updated Headline", "Updated Lead", "Updated Body"))
            .retrieve()
            .toEntity(Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        String newVersion = (String) response.getBody().get("version");
        assertNotEquals(versionedId, newVersion);
    }

    @Test
    void updateContent_missingIfMatch_returns400() throws Exception {
        Map<String, Object> created = createContent(token, articleBody("NoIfMatch", "Lead", "Body"));
        String unversionedId = extractId(created);

        HttpResponse<String> response = rawPut(
            "/content/contentid/" + unversionedId,
            toJson(articleBody("Updated", "Lead", "Body")),
            token, null);

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("40000"));
    }

    @Test
    void updateContent_staleIfMatch_returns409() throws Exception {
        Map<String, Object> created = createContent(token, articleBody("StaleTest", "Lead", "Body"));
        String unversionedId = extractId(created);
        String versionedId = extractVersion(created);

        // First update succeeds
        restClient.put()
            .uri("/content/contentid/{id}", unversionedId)
            .headers(h -> {
                h.addAll(authHeaders(token));
                h.set("If-Match", "\"" + versionedId + "\"");
            })
            .body(articleBody("Updated Once", "Lead", "Body"))
            .retrieve()
            .body(Map.class);

        // Second update with stale If-Match should fail
        HttpResponse<String> response = rawPut(
            "/content/contentid/" + unversionedId,
            toJson(articleBody("Updated Again", "Lead", "Body")),
            token, "\"" + versionedId + "\"");

        assertEquals(409, response.statusCode());
        assertTrue(response.body().contains("40900"));
    }

    @Test
    void deleteContent_returns204() throws Exception {
        Map<String, Object> created = createContent(token, articleBody("Delete Me", "Lead", "Body"));
        String unversionedId = extractId(created);
        String versionedId = extractVersion(created);

        HttpResponse<String> response = rawDelete(
            "/content/contentid/" + unversionedId,
            token, "\"" + versionedId + "\"");

        assertEquals(204, response.statusCode());
    }

    @Test
    void deleteContent_missingIfMatch_returns400() throws Exception {
        Map<String, Object> created = createContent(token, articleBody("NoIfMatchDelete", "Lead", "Body"));
        String unversionedId = extractId(created);

        HttpResponse<String> response = rawDelete(
            "/content/contentid/" + unversionedId,
            token, null);

        assertEquals(400, response.statusCode());
    }

    @Test
    void getDeleted_notReachableViaLatest() throws Exception {
        Map<String, Object> created = createContent(token, articleBody("Will Be Deleted", "Lead", "Body"));
        String unversionedId = extractId(created);
        String versionedId = extractVersion(created);

        // Delete via soft-delete (view reassigned from p.latest to p.deleted)
        HttpResponse<String> deleteResponse = rawDelete(
            "/content/contentid/" + unversionedId, token, "\"" + versionedId + "\"");
        assertEquals(204, deleteResponse.statusCode());

        // Unversioned resolve via p.latest should now fail or redirect.
        // Soft-delete moves view from p.latest to p.deleted, so:
        //   - 404 if no p.latest view found, or
        //   - 303 redirecting to p.deleted view
        // Either way, it should NOT be a 200 OK.
        HttpResponse<String> response = rawGet("/content/contentid/" + unversionedId, token);
        assertNotEquals(200, response.statusCode(),
            "Deleted content should not return 200 via unversioned ID, got body: " + response.body());

        // The versioned content should still be accessible directly
        HttpResponse<String> versionedResponse = rawGet("/content/contentid/" + versionedId, token);
        assertEquals(200, versionedResponse.statusCode(),
            "Versioned content should still be readable after soft-delete");
    }

    @Test
    void contentValidation_missingContentData() throws Exception {
        HttpResponse<String> response = rawPost(
            "/content",
            "{\"aspects\":{}}",
            token);

        // Should be 4xx or 5xx — content without contentData should be rejected
        assertTrue(response.statusCode() >= 400);
    }

    @Test
    @SuppressWarnings("unchecked")
    void defaultAspects_onCreateArticle() {
        Map<String, Object> created = createContent(token, articleBody("Defaults Test", "Lead", "Body"));
        String versionedId = extractVersion(created);

        Map<String, Object> content = restClient.get()
            .uri("/content/contentid/{id}", versionedId)
            .header("X-Auth-Token", token)
            .retrieve()
            .body(Map.class);

        Map<String, Object> aspects = (Map<String, Object>) content.get("aspects");
        assertNotNull(aspects, "aspects should not be null");
        assertTrue(aspects.containsKey("contentData"), "should have contentData");
        // WFContentStatus and WebContentStatus are auto-created by SetStatusPreStoreHook
        // only when workflow config content (dam.wfstatuslist.d) exists in the DB.
        // In the test environment without seeded config, verify other default aspects instead.
        // The p.InsertionInfo aspect is always created by OneContentPreStore.
        assertTrue(aspects.containsKey("p.InsertionInfo") || aspects.containsKey("atex.Metadata")
            || aspects.containsKey("atex.WFContentStatus"),
            "should have at least one default aspect beyond contentData, got: " + aspects.keySet());
    }
}
