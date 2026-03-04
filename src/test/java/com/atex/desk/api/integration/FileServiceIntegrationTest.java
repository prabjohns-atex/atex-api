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
}
