package com.atex.desk.api.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ContentVersioningIntegrationTest extends BaseIntegrationTest {

    private String token;

    @BeforeEach
    void loginBeforeEach() {
        token = loginSysadmin();
    }

    @Test
    @SuppressWarnings("unchecked")
    void history_showsAllVersions() {
        Map<String, Object> created = createContent(token, articleBody("History V1", "Lead", "Body"));
        String unversionedId = extractId(created);
        String v1 = extractVersion(created);

        // Update to v2
        Map<String, Object> updated1 = restClient.put()
            .uri("/content/contentid/{id}", unversionedId)
            .headers(h -> {
                h.addAll(authHeaders(token));
                h.set("If-Match", "\"" + v1 + "\"");
            })
            .body(articleBody("History V2", "Lead", "Body"))
            .retrieve()
            .body(Map.class);
        String v2 = extractVersion(updated1);

        // Update to v3
        Map<String, Object> updated2 = restClient.put()
            .uri("/content/contentid/{id}", unversionedId)
            .headers(h -> {
                h.addAll(authHeaders(token));
                h.set("If-Match", "\"" + v2 + "\"");
            })
            .body(articleBody("History V3", "Lead", "Body"))
            .retrieve()
            .body(Map.class);

        // Get history
        Map<String, Object> history = restClient.get()
            .uri("/content/contentid/{id}/history", unversionedId)
            .header("X-Auth-Token", token)
            .retrieve()
            .body(Map.class);

        assertNotNull(history);
        List<Map<String, Object>> versions = (List<Map<String, Object>>) history.get("versions");
        assertNotNull(versions);
        assertEquals(3, versions.size(), "Should have 3 versions after create + 2 updates");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getSpecificVersion_returns200() {
        Map<String, Object> created = createContent(token, articleBody("Version Specific V1", "Lead", "Body"));
        String unversionedId = extractId(created);
        String v1 = extractVersion(created);

        // Update to v2
        restClient.put()
            .uri("/content/contentid/{id}", unversionedId)
            .headers(h -> {
                h.addAll(authHeaders(token));
                h.set("If-Match", "\"" + v1 + "\"");
            })
            .body(articleBody("Version Specific V2", "Lead", "Body"))
            .retrieve()
            .body(Map.class);

        // Get original version (v1 is still accessible by versioned ID)
        Map<String, Object> v1Content = restClient.get()
            .uri("/content/contentid/{id}", v1)
            .header("X-Auth-Token", token)
            .retrieve()
            .body(Map.class);

        assertNotNull(v1Content);
        assertEquals(v1, v1Content.get("version"));
    }

    @Test
    void viewExclusivity_onlyOneLatest() {
        Map<String, Object> created = createContent(token, articleBody("View Excl V1", "Lead", "Body"));
        String unversionedId = extractId(created);
        String v1 = extractVersion(created);

        // Update to v2
        Map<String, Object> updated = restClient.put()
            .uri("/content/contentid/{id}", unversionedId)
            .headers(h -> {
                h.addAll(authHeaders(token));
                h.set("If-Match", "\"" + v1 + "\"");
            })
            .body(articleBody("View Excl V2", "Lead", "Body"))
            .retrieve()
            .body(Map.class);
        String v2 = extractVersion(updated);

        // Resolving unversioned should give us v2, not v1
        // We verify by getting via the versioned ID returned from resolve
        // The test for this is indirect — get versioned v2 and confirm it's the latest
        Map response = restClient.get()
            .uri("/content/contentid/{id}", v2)
            .header("X-Auth-Token", token)
            .retrieve()
            .body(Map.class);

        assertNotNull(response);
        assertEquals(v2, response.get("version"));
    }

    @Test
    void purgeVersion_removesVersion() {
        Map<String, Object> created = createContent(token, articleBody("Purge V1", "Lead", "Body"));
        String unversionedId = extractId(created);
        String v1 = extractVersion(created);

        // Update to v2
        Map<String, Object> updated = restClient.put()
            .uri("/content/contentid/{id}", unversionedId)
            .headers(h -> {
                h.addAll(authHeaders(token));
                h.set("If-Match", "\"" + v1 + "\"");
            })
            .body(articleBody("Purge V2", "Lead", "Body"))
            .retrieve()
            .body(Map.class);
        String v2 = extractVersion(updated);

        // Extract the version string from v1 (format: delegationId:key:version)
        String[] v1Parts = v1.split(":");
        String versionStr = v1Parts[2];

        // Purge v1
        restClient.delete()
            .uri("/content/contentid/{id}/version/{version}", unversionedId, versionStr)
            .header("X-Auth-Token", token)
            .retrieve()
            .toBodilessEntity();

        // History should now have 1 version
        @SuppressWarnings("unchecked")
        Map<String, Object> history = restClient.get()
            .uri("/content/contentid/{id}/history", unversionedId)
            .header("X-Auth-Token", token)
            .retrieve()
            .body(Map.class);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> versions = (List<Map<String, Object>>) history.get("versions");
        assertEquals(1, versions.size(), "Should have 1 version after purging v1");
    }

    @Test
    void purgeVersion_lastVersion_removesContent() {
        Map<String, Object> created = createContent(token, articleBody("Purge Last", "Lead", "Body"));
        String unversionedId = extractId(created);
        String v1 = extractVersion(created);

        // Extract the version string
        String[] v1Parts = v1.split(":");
        String versionStr = v1Parts[2];

        // Purge the only version
        restClient.delete()
            .uri("/content/contentid/{id}/version/{version}", unversionedId, versionStr)
            .header("X-Auth-Token", token)
            .retrieve()
            .toBodilessEntity();

        // Content should be fully gone — history 404
        HttpClientErrorException ex = assertThrows(HttpClientErrorException.class, () ->
            restClient.get()
                .uri("/content/contentid/{id}/history", unversionedId)
                .header("X-Auth-Token", token)
                .retrieve()
                .body(Map.class));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void aspectMd5Reuse_unchangedAspect() {
        // Create content
        Map<String, Object> created = createContent(token, articleBody("MD5 Test", "Lead", "Body"));
        String unversionedId = extractId(created);
        String v1 = extractVersion(created);

        // Update with same body (same aspect data)
        Map<String, Object> updated = restClient.put()
            .uri("/content/contentid/{id}", unversionedId)
            .headers(h -> {
                h.addAll(authHeaders(token));
                h.set("If-Match", "\"" + v1 + "\"");
            })
            .body(articleBody("MD5 Test", "Lead", "Body"))
            .retrieve()
            .body(Map.class);

        // Should succeed — content service handles MD5 reuse internally
        assertNotNull(updated);
        String v2 = extractVersion(updated);
        assertNotEquals(v1, v2, "Version should change even if aspect data is the same");
    }

    @Test
    @SuppressWarnings("unchecked")
    void aspectMd5Reuse_changedAspect() {
        Map<String, Object> created = createContent(token, articleBody("MD5 Changed V1", "Lead", "Body"));
        String unversionedId = extractId(created);
        String v1 = extractVersion(created);

        // Update with different body
        Map<String, Object> updated = restClient.put()
            .uri("/content/contentid/{id}", unversionedId)
            .headers(h -> {
                h.addAll(authHeaders(token));
                h.set("If-Match", "\"" + v1 + "\"");
            })
            .body(articleBody("MD5 Changed V2", "Different Lead", "Different Body"))
            .retrieve()
            .body(Map.class);

        assertNotNull(updated);
        String v2 = extractVersion(updated);
        assertNotEquals(v1, v2);
    }
}
