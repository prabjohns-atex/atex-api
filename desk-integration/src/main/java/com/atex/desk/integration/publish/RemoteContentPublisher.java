package com.atex.desk.integration.publish;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HTTP client for publishing content to a remote CMS.
 * Ported from gong/desk ContentPublisher interface + ContentAPIPublisher.
 *
 * <p>Handles REST API calls to the remote CMS including content CRUD,
 * binary upload, and external ID resolution.
 */
public class RemoteContentPublisher {

    private static final Logger LOG = Logger.getLogger(RemoteContentPublisher.class.getName());

    private final String baseUrl;
    private final String authToken;
    private final HttpClient httpClient;

    public RemoteContentPublisher(String baseUrl, String authToken) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.authToken = authToken;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }

    /**
     * Publish binary file to remote CMS.
     */
    public String publishBinary(InputStream data, String filePath, String mimeType) throws IOException {
        try {
            byte[] bytes = data.readAllBytes();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/file/upload"))
                .header("X-Auth-Token", authToken)
                .header("Content-Type", mimeType != null ? mimeType : "application/octet-stream")
                .header("X-File-Path", filePath)
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IOException("Binary upload failed: HTTP " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Binary upload interrupted", e);
        }
    }

    /**
     * Create content on remote CMS.
     */
    public String publishContent(String jsonRequest) throws IOException {
        return post("/content/contentid", jsonRequest);
    }

    /**
     * Update existing content on remote CMS.
     */
    public String publishContentUpdate(String contentId, String jsonRequest) throws IOException {
        return put("/content/contentid/" + contentId, jsonRequest);
    }

    /**
     * Unpublish (delete) content from remote CMS.
     */
    public String unpublish(String remoteContentId) throws IOException {
        return delete("/content/contentid/" + remoteContentId);
    }

    /**
     * Resolve an external ID to a content ID on the remote CMS.
     *
     * @return the resolved content ID, or null if not found
     */
    public String resolve(String externalId) throws IOException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/content/externalid/" + externalId))
                .header("X-Auth-Token", authToken)
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) return null;
            if (response.statusCode() >= 400) {
                throw new IOException("Resolve failed: HTTP " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Resolve interrupted", e);
        }
    }

    /**
     * Get content from remote CMS.
     */
    public String getContent(String contentId) throws IOException {
        return get("/content/contentid/" + contentId);
    }

    private String post(String path, String jsonBody) throws IOException {
        return sendJson("POST", path, jsonBody);
    }

    private String put(String path, String jsonBody) throws IOException {
        return sendJson("PUT", path, jsonBody);
    }

    private String get(String path) throws IOException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("X-Auth-Token", authToken)
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IOException("GET " + path + " failed: HTTP " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted", e);
        }
    }

    private String delete(String path) throws IOException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("X-Auth-Token", authToken)
                .DELETE()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IOException("DELETE " + path + " failed: HTTP " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted", e);
        }
    }

    private String sendJson(String method, String path, String jsonBody) throws IOException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("X-Auth-Token", authToken)
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                LOG.warning(method + " " + path + " failed: HTTP " + response.statusCode()
                    + " body=" + response.body());
                throw new IOException(method + " " + path + " failed: HTTP " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted", e);
        }
    }
}
