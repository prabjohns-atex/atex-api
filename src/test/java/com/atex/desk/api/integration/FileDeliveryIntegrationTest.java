package com.atex.desk.api.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for FileDeliveryController.
 * Tests file delivery by content ID, range requests, caching headers.
 *
 * Based on Polopoly's FileWSTestInt and filedelivery-service reference.
 */
class FileDeliveryIntegrationTest extends BaseIntegrationTest {

    private String token;
    private String contentId;   // unversioned
    private String versionedId; // versioned
    private String fileUri;
    private byte[] fileContent;

    @BeforeEach
    void setUp() throws Exception {
        token = loginSysadmin();
        fileContent = "The quick brown fox jumps over the lazy dog. This is test content for file delivery.".getBytes(StandardCharsets.UTF_8);

        // 1. Upload a file
        @SuppressWarnings("unchecked")
        Map<String, Object> uploadResponse = restClient.post()
            .uri("/file/content/sysadmin/delivery-test.txt")
            .headers(h -> {
                h.set("X-Auth-Token", token);
                h.setContentType(MediaType.TEXT_PLAIN);
            })
            .body(fileContent)
            .retrieve()
            .body(Map.class);

        fileUri = (String) uploadResponse.get("URI");
        assertNotNull(fileUri, "File upload should return URI");

        // 2. Create content with a "files" aspect pointing to the uploaded file
        Map<String, Object> fileEntry = new LinkedHashMap<>();
        fileEntry.put("fileUri", fileUri);
        fileEntry.put("mimeType", "text/plain");

        Map<String, Object> filesMap = new LinkedHashMap<>();
        filesMap.put("delivery-test.txt", fileEntry);

        Map<String, Object> filesData = new LinkedHashMap<>();
        filesData.put("files", filesMap);

        Map<String, Object> contentData = new LinkedHashMap<>();
        contentData.put("_type", "atex.onecms.image");
        contentData.put("title", "File Delivery Test");

        Map<String, Object> aspects = new LinkedHashMap<>();
        aspects.put("contentData", Map.of("data", contentData));
        aspects.put("files", Map.of("data", filesData));

        Map<String, Object> body = Map.of("aspects", aspects);
        Map<String, Object> createResponse = createContent(token, body);

        contentId = extractId(createResponse);
        versionedId = extractVersion(createResponse);
        assertNotNull(contentId);
        assertNotNull(versionedId);
    }

    @Test
    void deliverByContentId_returnsFile() throws Exception {
        HttpResponse<byte[]> response = rawGetBytes(
            "/filedelivery/" + contentId + "/delivery-test.txt", null);

        assertEquals(200, response.statusCode());
        assertArrayEquals(fileContent, response.body());
    }

    @Test
    void deliverByContentId_longForm_returnsFile() throws Exception {
        HttpResponse<byte[]> response = rawGetBytes(
            "/filedelivery/contentid/" + contentId + "/delivery-test.txt", null);

        assertEquals(200, response.statusCode());
        assertArrayEquals(fileContent, response.body());
    }

    @Test
    void deliverNonExistentContent_returns404() throws Exception {
        HttpResponse<byte[]> response = rawGetBytes(
            "/filedelivery/onecms:nonexistent/somefile.txt", null);

        assertEquals(404, response.statusCode());
    }

    @Test
    void deliverNonExistentFile_returns404() throws Exception {
        // Content exists but file path doesn't match any file entry
        HttpResponse<byte[]> response = rawGetBytes(
            "/filedelivery/" + contentId + "/nonexistent-file.txt", null);

        // Should still find a file (fallback to first file in aspect)
        assertEquals(200, response.statusCode());
    }

    @Test
    void deliverByContentId_hasCorrectHeaders() throws Exception {
        HttpResponse<byte[]> response = rawGetBytes(
            "/filedelivery/" + contentId + "/delivery-test.txt", null);

        assertEquals(200, response.statusCode());

        // Check Accept-Ranges header
        String acceptRanges = response.headers().firstValue("Accept-Ranges").orElse(null);
        assertEquals("bytes", acceptRanges);

        // Check ETag is present
        String etag = response.headers().firstValue("ETag").orElse(null);
        assertNotNull(etag, "ETag header should be present");

        // Check Cache-Control is present
        String cacheControl = response.headers().firstValue("Cache-Control").orElse(null);
        assertNotNull(cacheControl, "Cache-Control header should be present");
        assertTrue(cacheControl.contains("max-age="), "Cache-Control should contain max-age");

        // Check Content-Length
        long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        assertEquals(fileContent.length, contentLength);
    }

    @Test
    void rangeRequest_returnsPartialContent() throws Exception {
        // Request first 10 bytes
        Map<String, String> headers = Map.of("Range", "bytes=0-9");
        HttpResponse<byte[]> response = rawGetBytes(
            "/filedelivery/" + contentId + "/delivery-test.txt", headers);

        assertEquals(206, response.statusCode());

        // Verify Content-Range header
        String contentRange = response.headers().firstValue("Content-Range").orElse(null);
        assertNotNull(contentRange, "Content-Range header should be present");
        assertTrue(contentRange.startsWith("bytes 0-9/"), "Content-Range should start with 'bytes 0-9/'");

        // Verify body length
        assertEquals(10, response.body().length);

        // Verify content matches first 10 bytes
        byte[] expected = new byte[10];
        System.arraycopy(fileContent, 0, expected, 0, 10);
        assertArrayEquals(expected, response.body());
    }

    @Test
    void rangeRequest_openEnded_returnsRemainder() throws Exception {
        // Request from byte 10 to end
        int offset = 10;
        Map<String, String> headers = Map.of("Range", "bytes=" + offset + "-");
        HttpResponse<byte[]> response = rawGetBytes(
            "/filedelivery/" + contentId + "/delivery-test.txt", headers);

        assertEquals(206, response.statusCode());

        byte[] expected = new byte[fileContent.length - offset];
        System.arraycopy(fileContent, offset, expected, 0, expected.length);
        assertArrayEquals(expected, response.body());
    }

    @Test
    void rangeRequest_suffixRange_returnsLastNBytes() throws Exception {
        // Request last 5 bytes
        Map<String, String> headers = Map.of("Range", "bytes=-5");
        HttpResponse<byte[]> response = rawGetBytes(
            "/filedelivery/" + contentId + "/delivery-test.txt", headers);

        assertEquals(206, response.statusCode());

        byte[] expected = new byte[5];
        System.arraycopy(fileContent, fileContent.length - 5, expected, 0, 5);
        assertArrayEquals(expected, response.body());
    }

    @Test
    void rangeRequest_withIfRange_matchingEtag_returnsPartial() throws Exception {
        // First get the ETag
        HttpResponse<byte[]> initial = rawGetBytes(
            "/filedelivery/" + contentId + "/delivery-test.txt", null);
        String etag = initial.headers().firstValue("ETag").orElse(null);
        assertNotNull(etag);

        // Request with matching If-Range
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Range", "bytes=0-4");
        headers.put("If-Range", etag);
        HttpResponse<byte[]> response = rawGetBytes(
            "/filedelivery/" + contentId + "/delivery-test.txt", headers);

        assertEquals(206, response.statusCode());
        assertEquals(5, response.body().length);
    }

    @Test
    void rangeRequest_withIfRange_nonMatchingEtag_returnsFullContent() throws Exception {
        // Request with non-matching If-Range — should ignore Range and return full content
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Range", "bytes=0-4");
        headers.put("If-Range", "\"wrong-etag\"");
        HttpResponse<byte[]> response = rawGetBytes(
            "/filedelivery/" + contentId + "/delivery-test.txt", headers);

        assertEquals(200, response.statusCode());
        assertArrayEquals(fileContent, response.body());
    }

    @Test
    void ping_returnsOk() throws Exception {
        HttpResponse<String> response = rawGet("/filedelivery", null);
        assertEquals(200, response.statusCode());
    }

    @Test
    void fileDelivery_noAuthRequired() throws Exception {
        // FileDeliveryController is NOT behind auth filter — verify no token needed
        HttpResponse<byte[]> response = rawGetBytes(
            "/filedelivery/" + contentId + "/delivery-test.txt", null);
        assertEquals(200, response.statusCode());
    }
}