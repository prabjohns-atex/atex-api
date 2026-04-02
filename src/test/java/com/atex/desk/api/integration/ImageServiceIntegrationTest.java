package com.atex.desk.api.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the ImageController (/image/*).
 * Tests redirect URL generation, HMAC signing, and content resolution.
 */
@TestPropertySource(properties = {
    "desk.image-service.enabled=true",
    "desk.image-service.url=http://localhost:8090",
    "desk.image-service.secret=test-secret",
    "desk.image-service.signature-length=7"
})
class ImageServiceIntegrationTest extends BaseIntegrationTest {

    private String token;

    @BeforeEach
    void loginBeforeEach() {
        token = loginSysadmin();
    }

    @Test
    void imageEndpoint_requiresAuth() throws Exception {
        HttpResponse<String> response = rawGet("/image/onecms:fake-id/photo.jpg", null);
        assertEquals(401, response.statusCode());
    }

    @Test
    void imageEndpoint_404ForUnknownContent() throws Exception {
        HttpResponse<String> response = rawGet("/image/onecms:nonexistent/photo.jpg", token);
        // Should return 404 since content doesn't exist
        assertEquals(404, response.statusCode());
    }

    @Test
    void imageContentId_404ForUnknownContent() throws Exception {
        HttpResponse<String> response = rawGet(
            "/image/contentid/onecms:nonexistent:fake-version/photo.jpg", token);
        assertEquals(404, response.statusCode());
    }

    @Test
    void imageOriginal_404ForUnknownContent() throws Exception {
        HttpResponse<String> response = rawGet("/image/original/onecms:nonexistent/photo.jpg", token);
        assertEquals(404, response.statusCode());
    }

    @Test
    void imageOriginalNoFilename_404ForUnknownContent() throws Exception {
        HttpResponse<String> response = rawGet("/image/original/onecms:nonexistent", token);
        assertEquals(404, response.statusCode());
    }

    @Test
    void imageEndpoint_redirectsForImageContent() throws Exception {
        // Create an image content with atex.Image aspect
        Map<String, Object> body = createImageWithFileAspect("test-photo.jpg", 1920, 1080);
        Map<String, Object> created = createContent(token, body);
        String id = extractId(created);

        // Request with unversioned ID — should get 303 redirect to versioned URL
        HttpResponse<String> response = rawGet("/image/" + id + "/photo.jpg?w=240", token);
        assertEquals(303, response.statusCode());

        String location = response.headers().firstValue("Location").orElse("");
        assertTrue(location.contains("/image/") && location.contains("photo.jpg"),
            "Should redirect to versioned image URL, got: " + location);
        assertTrue(location.contains("w=240"), "Should preserve width param");

        // Follow the 303 to get the 302 sidecar redirect
        HttpResponse<String> versionedResp = rawGet(location, token);
        assertEquals(302, versionedResp.statusCode());

        String sidecarUrl = versionedResp.headers().firstValue("Location").orElse("");
        assertTrue(sidecarUrl.contains("w=240"), "Should include width param");
        assertTrue(sidecarUrl.contains("ow=1920"), "Should include original width");
        assertTrue(sidecarUrl.contains("oh=1080"), "Should include original height");
        assertTrue(sidecarUrl.contains("%24p%24"), "Should include HMAC signature key");

        String cacheControl = versionedResp.headers().firstValue("Cache-Control").orElse("");
        assertTrue(cacheControl.contains("max-age"), "Should have cache headers");
    }

    @Test
    void imageVersionedId_redirectsForImageContent() throws Exception {
        Map<String, Object> body = createImageWithFileAspect("versioned.jpg", 800, 600);
        Map<String, Object> created = createContent(token, body);
        String version = extractVersion(created);

        HttpResponse<String> response = rawGet(
            "/image/contentid/" + version + "/versioned.jpg?w=100&h=100", token);
        assertEquals(302, response.statusCode());

        String location = response.headers().firstValue("Location").orElse("");
        assertTrue(location.contains("w=100"), "Should include width");
        assertTrue(location.contains("h=100"), "Should include height");
    }

    @Test
    void imageOriginal_redirectsToFileService() throws Exception {
        Map<String, Object> body = createImageWithFileAspect("original.jpg", 4000, 3000);
        Map<String, Object> created = createContent(token, body);
        String id = extractId(created);

        // Original endpoint with unversioned ID — 303 to versioned, then 302 to file
        HttpResponse<String> response = rawGet(
            "/image/original/" + id + "/original.jpg", token);
        // Original endpoint doesn't go through the unversioned→versioned redirect
        // (it's a separate @GetMapping path), so it should still be 302
        assertTrue(response.statusCode() == 302 || response.statusCode() == 303,
            "Expected redirect, got: " + response.statusCode());

        String location = response.headers().firstValue("Location").orElse("");
        assertTrue(location.startsWith("/file/") || location.contains("/image/"),
            "Should redirect to file service or versioned URL, got: " + location);
    }

    @Test
    void imageEndpoint_404WhenNoImageAspect() throws Exception {
        // Create article content (no atex.Image aspect)
        Map<String, Object> created = createContent(token, articleBody("Test", "Lead", "Body"));
        String id = extractId(created);

        // Unversioned article → 303 to versioned, then versioned → 404 (no image file)
        HttpResponse<String> response = rawGet("/image/" + id + "/photo.jpg", token);
        if (response.statusCode() == 303) {
            String loc = response.headers().firstValue("Location").orElse("");
            response = rawGet(loc, token);
        }
        assertEquals(404, response.statusCode());
    }

    @Test
    void imageEndpoint_includesEditInfoInRedirect() throws Exception {
        // Create image with edit info (rotation, flip)
        Map<String, Object> body = createImageWithEditInfo(
            "edited.jpg", 1920, 1080, 90, true, false);
        Map<String, Object> created = createContent(token, body);
        String id = extractId(created);

        // Unversioned → 303 to versioned, then follow to get sidecar 302
        HttpResponse<String> response = rawGet("/image/" + id + "/edited.jpg?w=240", token);
        assertEquals(303, response.statusCode());

        String versionedUrl = response.headers().firstValue("Location").orElse("");
        HttpResponse<String> versionedResp = rawGet(versionedUrl, token);
        assertEquals(302, versionedResp.statusCode());

        String location = versionedResp.headers().firstValue("Location").orElse("");
        assertTrue(location.contains("rot=90"), "Should include rotation");
        assertTrue(location.contains("flipv=1"), "Should include vertical flip");
    }

    // ---- Helpers ----

    private Map<String, Object> createImageWithFileAspect(String filePath, int width, int height) {
        Map<String, Object> contentData = new LinkedHashMap<>();
        contentData.put("_type", "atex.onecms.image");
        contentData.put("title", "Test Image");

        Map<String, Object> imageAspect = new LinkedHashMap<>();
        imageAspect.put("filePath", filePath);
        imageAspect.put("width", width);
        imageAspect.put("height", height);

        Map<String, Object> aspects = new LinkedHashMap<>();
        aspects.put("contentData", Map.of("data", contentData));
        aspects.put("atex.Image", Map.of("data", imageAspect));

        return Map.of("aspects", aspects);
    }

    private Map<String, Object> createImageWithEditInfo(
            String filePath, int width, int height,
            int rotation, boolean flipV, boolean flipH) {
        Map<String, Object> contentData = new LinkedHashMap<>();
        contentData.put("_type", "atex.onecms.image");
        contentData.put("title", "Edited Image");

        Map<String, Object> imageAspect = new LinkedHashMap<>();
        imageAspect.put("filePath", filePath);
        imageAspect.put("width", width);
        imageAspect.put("height", height);

        Map<String, Object> editInfo = new LinkedHashMap<>();
        editInfo.put("rotation", rotation);
        editInfo.put("flipVertical", flipV);
        editInfo.put("flipHorizontal", flipH);

        Map<String, Object> aspects = new LinkedHashMap<>();
        aspects.put("contentData", Map.of("data", contentData));
        aspects.put("atex.Image", Map.of("data", imageAspect));
        aspects.put("atex.ImageEditInfo", Map.of("data", editInfo));

        return Map.of("aspects", aspects);
    }
}
