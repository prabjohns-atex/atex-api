package com.atex.desk.api.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FileServiceIntegrationTest extends BaseIntegrationTest {

    private String token;

    @BeforeEach
    void loginBeforeEach() {
        token = loginSysadmin();
    }

    @Test
    void uploadFile_returns201() {
        byte[] fileContent = "Hello, file service!".getBytes(StandardCharsets.UTF_8);

        ResponseEntity<Map> response = restClient.post()
            .uri("/file/content/sysadmin/test-upload.txt")
            .headers(h -> {
                h.set("X-Auth-Token", token);
                h.setContentType(MediaType.TEXT_PLAIN);
            })
            .body(fileContent)
            .retrieve()
            .toEntity(Map.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getHeaders().getLocation());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().get("URI"));
    }

    @Test
    void downloadFile_returnsSameBytes() {
        byte[] original = "Download test content".getBytes(StandardCharsets.UTF_8);

        // Upload
        @SuppressWarnings("unchecked")
        Map<String, Object> uploadResponse = restClient.post()
            .uri("/file/content/sysadmin/download-test.txt")
            .headers(h -> {
                h.set("X-Auth-Token", token);
                h.setContentType(MediaType.TEXT_PLAIN);
            })
            .body(original)
            .retrieve()
            .body(Map.class);

        String fileUri = (String) uploadResponse.get("URI");
        assertNotNull(fileUri);

        // Download using parsed URI components
        byte[] downloaded = restClient.get()
            .uri(toFileUrl(fileUri))
            .header("X-Auth-Token", token)
            .retrieve()
            .body(byte[].class);

        assertArrayEquals(original, downloaded);
    }

    @Test
    void fileMetadata_returnsInfo() {
        byte[] fileContent = "Metadata test".getBytes(StandardCharsets.UTF_8);

        // Upload
        @SuppressWarnings("unchecked")
        Map<String, Object> uploadResponse = restClient.post()
            .uri("/file/content/sysadmin/metadata-test.txt")
            .headers(h -> {
                h.set("X-Auth-Token", token);
                h.setContentType(MediaType.TEXT_PLAIN);
            })
            .body(fileContent)
            .retrieve()
            .body(Map.class);

        String fileUri = (String) uploadResponse.get("URI");

        // Get info
        @SuppressWarnings("unchecked")
        Map<String, Object> info = restClient.get()
            .uri(toFileInfoUrl(fileUri))
            .header("X-Auth-Token", token)
            .retrieve()
            .body(Map.class);

        assertNotNull(info);
        assertNotNull(info.get("URI"));
        assertNotNull(info.get("length"));
        assertTrue(((Number) info.get("length")).longValue() > 0);
    }

    @Test
    void deleteFile_removes() throws Exception {
        byte[] fileContent = "Delete me".getBytes(StandardCharsets.UTF_8);

        // Upload
        @SuppressWarnings("unchecked")
        Map<String, Object> uploadResponse = restClient.post()
            .uri("/file/content/sysadmin/delete-test.txt")
            .headers(h -> {
                h.set("X-Auth-Token", token);
                h.setContentType(MediaType.TEXT_PLAIN);
            })
            .body(fileContent)
            .retrieve()
            .body(Map.class);

        String fileUri = (String) uploadResponse.get("URI");
        String deleteUrl = toFileUrl(fileUri);
        String infoUrl = toFileInfoUrl(fileUri);

        // Delete
        HttpResponse<String> deleteResp = rawDelete(deleteUrl, token, null);
        assertTrue(deleteResp.statusCode() >= 200 && deleteResp.statusCode() < 300,
            "Delete should succeed, got: " + deleteResp.statusCode());

        // Get should fail — file no longer exists
        HttpResponse<String> getResp = rawGet(infoUrl, token);
        assertTrue(getResp.statusCode() >= 400,
            "GET after delete should fail, got: " + getResp.statusCode());
    }

    @Test
    void fileMetadataByUri_returnsInfo() {
        byte[] fileContent = "URI query test".getBytes(StandardCharsets.UTF_8);

        // Upload
        @SuppressWarnings("unchecked")
        Map<String, Object> uploadResponse = restClient.post()
            .uri("/file/content/sysadmin/uri-query-test.txt")
            .headers(h -> {
                h.set("X-Auth-Token", token);
                h.setContentType(MediaType.TEXT_PLAIN);
            })
            .body(fileContent)
            .retrieve()
            .body(Map.class);

        String uri = (String) uploadResponse.get("URI");

        // Get metadata by URI query param
        @SuppressWarnings("unchecked")
        Map<String, Object> info = restClient.get()
            .uri("/file/metadata?uri={uri}", uri)
            .header("X-Auth-Token", token)
            .retrieve()
            .body(Map.class);

        assertNotNull(info);
        assertEquals(uri, info.get("URI"));
    }

    // --- URI helpers ---
    // File URIs are like "content://sysadmin/uuid.txt"
    // FileController expects /file/{space}/{host}/{path:.*}
    // so we need: /file/content/sysadmin/uuid.txt

    private String toFileUrl(String fileUri) {
        URI parsed = URI.create(fileUri);
        return "/file/" + parsed.getScheme() + "/" + parsed.getHost() + parsed.getPath();
    }

    private String toFileInfoUrl(String fileUri) {
        URI parsed = URI.create(fileUri);
        return "/file/info/" + parsed.getScheme() + "/" + parsed.getHost() + parsed.getPath();
    }

    @Test
    void uploadDifferentTypes_correctMimeTypes() {
        // Upload a PNG (simulated)
        byte[] pngBytes = new byte[]{(byte) 0x89, 'P', 'N', 'G'};
        @SuppressWarnings("unchecked")
        Map<String, Object> pngResponse = restClient.post()
            .uri("/file/content/sysadmin/test.png")
            .headers(h -> {
                h.set("X-Auth-Token", token);
                h.setContentType(MediaType.IMAGE_PNG);
            })
            .body(pngBytes)
            .retrieve()
            .body(Map.class);

        assertNotNull(pngResponse.get("URI"));
        assertEquals("image/png", pngResponse.get("mimeType"));
    }

    // --- Auth tests (based on Polopoly FileWSTestInt) ---

    @Test
    void uploadWithoutAuth_returns401() throws Exception {
        HttpResponse<String> response = rawRequest("POST", "/file/content/sysadmin/noauth.txt",
            "test content", null);

        assertEquals(401, response.statusCode());
        // Reference: WWW-Authenticate header should be present
        String wwwAuth = response.headers().firstValue("WWW-Authenticate").orElse(null);
        assertNotNull(wwwAuth, "401 response should include WWW-Authenticate header");
    }

    @Test
    void downloadWithoutAuth_returns401() throws Exception {
        // Upload a file first (with auth)
        @SuppressWarnings("unchecked")
        Map<String, Object> uploadResponse = restClient.post()
            .uri("/file/content/sysadmin/auth-download-test.txt")
            .headers(h -> {
                h.set("X-Auth-Token", token);
                h.setContentType(MediaType.TEXT_PLAIN);
            })
            .body("auth test content".getBytes(StandardCharsets.UTF_8))
            .retrieve()
            .body(Map.class);

        String fileUri = (String) uploadResponse.get("URI");
        String downloadUrl = toFileUrl(fileUri);

        // Try to download without auth
        HttpResponse<String> response = rawGet(downloadUrl, null);
        assertEquals(401, response.statusCode());
    }

    @Test
    void deleteWithoutAuth_returns401() throws Exception {
        // Upload a file first (with auth)
        @SuppressWarnings("unchecked")
        Map<String, Object> uploadResponse = restClient.post()
            .uri("/file/content/sysadmin/auth-delete-test.txt")
            .headers(h -> {
                h.set("X-Auth-Token", token);
                h.setContentType(MediaType.TEXT_PLAIN);
            })
            .body("auth delete test".getBytes(StandardCharsets.UTF_8))
            .retrieve()
            .body(Map.class);

        String fileUri = (String) uploadResponse.get("URI");
        String deleteUrl = toFileUrl(fileUri);

        // Try to delete without auth
        HttpResponse<String> response = rawDelete(deleteUrl, null, null);
        assertEquals(401, response.statusCode());
    }

    @Test
    void getNonExistentFile_returns404() throws Exception {
        HttpResponse<String> response = rawGet("/file/content/sysadmin/does-not-exist.txt", token);
        assertTrue(response.statusCode() >= 400,
            "GET non-existent file should return 4xx, got: " + response.statusCode());
    }

    @Test
    void getInfoNonExistentFile_returns404() throws Exception {
        HttpResponse<String> response = rawGet("/file/info/content/sysadmin/does-not-exist.txt", token);
        assertTrue(response.statusCode() >= 400,
            "GET info for non-existent file should return 4xx, got: " + response.statusCode());
    }

    @Test
    void uploadFile_returnsLocationHeader() {
        byte[] fileContent = "Location header test".getBytes(StandardCharsets.UTF_8);

        ResponseEntity<Map> response = restClient.post()
            .uri("/file/content/sysadmin/location-test.txt")
            .headers(h -> {
                h.set("X-Auth-Token", token);
                h.setContentType(MediaType.TEXT_PLAIN);
            })
            .body(fileContent)
            .retrieve()
            .toEntity(Map.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        URI location = response.getHeaders().getLocation();
        assertNotNull(location, "Upload response should include Location header");
        assertTrue(location.toString().contains("/file/"), "Location should point to /file/ path");
    }

    @Test
    void uploadFile_responseContainsExpectedFields() {
        byte[] fileContent = "Field test".getBytes(StandardCharsets.UTF_8);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
            .uri("/file/content/sysadmin/fields-test.txt")
            .headers(h -> {
                h.set("X-Auth-Token", token);
                h.setContentType(MediaType.TEXT_PLAIN);
            })
            .body(fileContent)
            .retrieve()
            .body(Map.class);

        // Reference: FileInfoDTO should contain URI, mimeType, length, originalPath
        assertNotNull(response.get("URI"), "Response should contain URI");
        assertNotNull(response.get("mimeType"), "Response should contain mimeType");
        assertNotNull(response.get("length"), "Response should contain length");
        assertEquals(fileContent.length, ((Number) response.get("length")).intValue(),
            "Length should match uploaded content size");
    }

    @Test
    void downloadFile_hasCacheHeaders() throws Exception {
        byte[] fileContent = "Cache test".getBytes(StandardCharsets.UTF_8);

        @SuppressWarnings("unchecked")
        Map<String, Object> uploadResponse = restClient.post()
            .uri("/file/content/sysadmin/cache-test.txt")
            .headers(h -> {
                h.set("X-Auth-Token", token);
                h.setContentType(MediaType.TEXT_PLAIN);
            })
            .body(fileContent)
            .retrieve()
            .body(Map.class);

        String fileUri = (String) uploadResponse.get("URI");
        String downloadUrl = toFileUrl(fileUri);

        HttpResponse<byte[]> response = rawGetBytes(downloadUrl, Map.of("X-Auth-Token", token));
        assertEquals(200, response.statusCode());

        String cacheControl = response.headers().firstValue("Cache-Control").orElse(null);
        assertNotNull(cacheControl, "Download should have Cache-Control header");
    }

    @Test
    void uploadToAnonymousEndpoint_usesCallerAsHost() {
        byte[] fileContent = "Anonymous upload".getBytes(StandardCharsets.UTF_8);

        ResponseEntity<Map> response = restClient.post()
            .uri("/file/content")
            .headers(h -> {
                h.set("X-Auth-Token", token);
                h.setContentType(MediaType.TEXT_PLAIN);
            })
            .body(fileContent)
            .retrieve()
            .toEntity(Map.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        String uri = (String) response.getBody().get("URI");
        assertNotNull(uri, "Anonymous upload should return URI");
        // URI host part should be the authenticated user's ID
        assertTrue(uri.contains("://"), "URI should have scheme://host format");
    }
}
