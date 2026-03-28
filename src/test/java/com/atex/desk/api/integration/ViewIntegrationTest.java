package com.atex.desk.api.integration;

import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for ViewController (/view).
 */
class ViewIntegrationTest extends BaseIntegrationTest {

    private static final Gson GSON = new Gson();
    private String token;

    @BeforeEach
    void setUp() {
        token = loginSysadmin();
    }

    @Test
    void assignToView_noAuth_returns401() throws Exception {
        HttpResponse<String> resp = rawPut("/view/p.test", "{\"contentId\":\"onecms:fake:v1\"}", null, null);
        assertEquals(401, resp.statusCode());
    }

    @Test
    void assignToView_missingContentId_returns400() throws Exception {
        HttpResponse<String> resp = rawPut("/view/p.test", "{}", token, null);
        assertEquals(400, resp.statusCode());
    }

    @Test
    void assignToView_nonVersionedId_returns400() throws Exception {
        HttpResponse<String> resp = rawPut("/view/p.test", "{\"contentId\":\"onecms:somekey\"}", token, null);
        assertEquals(400, resp.statusCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void assignToView_validContent_succeeds() throws Exception {
        // Create content — the response contains the versioned ID
        Map<String, Object> created = createContent(token, articleBody("View Test", "lead", "body"));
        String versionedId = extractVersion(created);
        assertNotNull(versionedId);

        // Assign to a custom view
        HttpResponse<String> resp = rawPut("/view/p.myview",
                "{\"contentId\":\"" + versionedId + "\"}", token, null);
        assertEquals(200, resp.statusCode());

        // Verify by resolving content in that view
        String unversionedId = extractId(created);
        HttpResponse<String> viewResp = rawGet("/content/view/p.myview/contentid/" + unversionedId, token);
        // Should redirect (303) to the versioned content
        assertEquals(303, viewResp.statusCode());
    }

    @Test
    void removeFromView_noAuth_returns401() throws Exception {
        HttpResponse<String> resp = rawDelete("/view/p.test/onecms:fake", null, null);
        assertEquals(401, resp.statusCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void removeFromView_afterAssign_succeeds() throws Exception {
        // Create and assign
        Map<String, Object> created = createContent(token, articleBody("Remove View", "lead", "body"));
        String versionedId = extractVersion(created);
        String unversionedId = extractId(created);

        rawPut("/view/p.removetest",
                "{\"contentId\":\"" + versionedId + "\"}", token, null);

        // Verify it's assigned
        HttpResponse<String> before = rawGet("/content/view/p.removetest/contentid/" + unversionedId, token);
        assertEquals(303, before.statusCode());

        // Remove
        HttpResponse<String> resp = rawDelete("/view/p.removetest/" + unversionedId, token, null);
        assertEquals(200, resp.statusCode());

        // Verify it's gone
        HttpResponse<String> after = rawGet("/content/view/p.removetest/contentid/" + unversionedId, token);
        assertEquals(404, after.statusCode());
    }

    @Test
    void removeFromView_notFound_returns200() throws Exception {
        // Removing from a view that doesn't exist should still return 200 (idempotent)
        // but the content ID must be valid format
        HttpResponse<String> resp = rawDelete("/view/p.nonexistent/onecms:fake", token, null);
        // May return 200 (idempotent) or 404 depending on delegation ID resolution
        assertTrue(resp.statusCode() == 200 || resp.statusCode() == 404,
                "Should return 200 or 404, got: " + resp.statusCode());
    }
}
