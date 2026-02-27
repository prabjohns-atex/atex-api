package com.atex.onecms.app.dam.publish;

import com.atex.onecms.app.dam.publish.config.RemoteConfigBean;
import com.atex.onecms.app.dam.util.HttpDamUtils;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.IdUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class ContentAPIPublisher implements ContentPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(ContentAPIPublisher.class);
    private static final String CONTENT_TYPE = "application/json";
    private static final int MAX_RETRIES = 2;

    private final RemoteConfigBean config;
    private final String username;
    private final UserTokenStorage tokenStorage;

    public ContentAPIPublisher(RemoteConfigBean config, String username, UserTokenStorage tokenStorage) {
        this.config = config;
        this.username = username;
        this.tokenStorage = tokenStorage;
    }

    @Override
    public PublishResult publishContent(String json) throws ContentPublisherException {
        String url = buildContentUrl();
        HttpDamUtils.WebServiceResponse response = executeWithRetry(
            CONTENT_TYPE, json, HttpDamUtils.WebServiceMethod.POST, url);
        if (response.isError()) {
            throw new ContentPublisherException("Failed to publish content: " + response.getErrorMessage());
        }
        String location = response.getHeader("Location");
        String id = extractIdFromLocation(location);
        return new PublishResult(id, null);
    }

    @Override
    public PublishResult publishContentUpdate(ContentId contentId, String json) throws ContentPublisherException {
        String url = buildContentUrl("contentid", IdUtil.toIdString(contentId));
        String etag = fetchETag(contentId);
        HttpDamUtils.WebServiceResponse response = executeWithRetry(
            CONTENT_TYPE, json, HttpDamUtils.WebServiceMethod.PUT, url, etag);
        if (response.isError()) {
            throw new ContentPublisherException("Failed to update content " + contentId + ": " + response.getErrorMessage());
        }
        return new PublishResult(IdUtil.toIdString(contentId), null);
    }

    @Override
    public PublishResult unpublish(ContentId contentId, String json) throws ContentPublisherException {
        String url = buildContentUrl("contentid", IdUtil.toIdString(contentId));
        HttpDamUtils.WebServiceResponse response = executeWithRetry(
            CONTENT_TYPE, json, HttpDamUtils.WebServiceMethod.DELETE, url);
        if (response.isError()) {
            throw new ContentPublisherException("Failed to unpublish " + contentId + ": " + response.getErrorMessage());
        }
        return new PublishResult(IdUtil.toIdString(contentId), null);
    }

    @Override
    public String publishBinary(InputStream is, String filePath, String mimeType) throws ContentPublisherException {
        String url = config.getRemoteFileServiceUrl();
        if (url == null || url.isEmpty()) {
            throw new ContentPublisherException("Remote file service URL not configured");
        }
        String token = getAuthToken();
        HttpDamUtils.WebServiceResponse response = HttpDamUtils.sendStreamToFileService(
            mimeType, is, url, token);
        if (response.isError()) {
            throw new ContentPublisherException("Failed to upload binary: " + response.getErrorMessage());
        }
        return response.getBody();
    }

    @Override
    public ByteArrayOutputStream readBinary(String uri) throws ContentPublisherException {
        String url = config.getRemoteFileServiceUrl();
        if (url == null || url.isEmpty()) {
            throw new ContentPublisherException("Remote file service URL not configured");
        }
        String fileUrl = url + "/" + uri;
        String token = getAuthToken();
        return HttpDamUtils.readBinaryContentFromURL(fileUrl, null, token);
    }

    @Override
    public ContentId resolve(String externalId) throws ContentPublisherException {
        String url = buildContentUrl("externalid", externalId);
        String token = getAuthToken();
        HttpDamUtils.WebServiceResponse response = HttpDamUtils.callDataApiWs(
            CONTENT_TYPE, null, HttpDamUtils.WebServiceMethod.GET, url, token, null);
        if (response.isError()) {
            if (response.getErrorMessage() != null && response.getErrorMessage().contains("404")) {
                throw new ContentPublisherNotFoundException("External ID not found: " + externalId);
            }
            throw new ContentPublisherException("Failed to resolve " + externalId + ": " + response.getErrorMessage());
        }
        // Parse content ID from response body
        String body = response.getBody();
        if (body != null && body.contains("\"id\"")) {
            try {
                int idStart = body.indexOf("\"id\"");
                int valStart = body.indexOf('"', idStart + 4);
                int valEnd = body.indexOf('"', valStart + 1);
                if (valStart >= 0 && valEnd >= 0) {
                    return IdUtil.fromString(body.substring(valStart + 1, valEnd));
                }
            } catch (Exception e) {
                LOG.debug("Cannot parse content ID from response: {}", body);
            }
        }
        return null;
    }

    @Override
    public String getContent(ContentId contentId) throws ContentPublisherException {
        String url = buildContentUrl("contentid", IdUtil.toIdString(contentId));
        String token = getAuthToken();
        HttpDamUtils.WebServiceResponse response = HttpDamUtils.callDataApiWs(
            CONTENT_TYPE, null, HttpDamUtils.WebServiceMethod.GET, url, token, null);
        if (response.isError()) {
            if (response.getErrorMessage() != null && response.getErrorMessage().contains("404")) {
                throw new ContentPublisherNotFoundException("Content not found: " + contentId);
            }
            throw new ContentPublisherException("Failed to get content " + contentId + ": " + response.getErrorMessage());
        }
        return response.getBody();
    }

    @Override
    public String getRemotePublicationUrl(ContentId sourceId, ContentId remoteId) {
        String apiUrl = config.getRemoteApiUrl();
        if (apiUrl == null || remoteId == null) return null;
        return apiUrl + "/content/contentid/" + IdUtil.toIdString(remoteId);
    }

    @Override
    public HttpDamUtils.WebServiceResponse postContent(String json, String... path) throws ContentPublisherException {
        String url = buildContentUrl(path);
        return executeWithRetry(CONTENT_TYPE, json, HttpDamUtils.WebServiceMethod.POST, url);
    }

    private String buildContentUrl(String... pathSegments) {
        String apiUrl = config.getRemoteApiUrl();
        if (apiUrl == null || apiUrl.isEmpty()) {
            throw new ContentPublisherException("Remote API URL not configured");
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(apiUrl)
            .pathSegment("content");
        for (String seg : pathSegments) {
            builder.pathSegment(seg);
        }
        return builder.build().toUriString();
    }

    private HttpDamUtils.WebServiceResponse executeWithRetry(String contentType, String json,
                                                              HttpDamUtils.WebServiceMethod method,
                                                              String url) {
        return executeWithRetry(contentType, json, method, url, null);
    }

    private HttpDamUtils.WebServiceResponse executeWithRetry(String contentType, String json,
                                                              HttpDamUtils.WebServiceMethod method,
                                                              String url, String etag) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            String token = getAuthToken();
            HttpDamUtils.WebServiceResponse response = HttpDamUtils.callDataApiWs(
                contentType, json, method, url, token, etag);

            if (!response.isError()) {
                return response;
            }

            // If auth failure, invalidate token and retry
            if (response.getErrorMessage() != null &&
                (response.getErrorMessage().contains("401") || response.getErrorMessage().contains("403"))) {
                LOG.debug("Auth failure on attempt {}, renewing token for user {}", attempt + 1, username);
                tokenStorage.invalidate(username);
                if (attempt < MAX_RETRIES - 1) {
                    continue;
                }
            }
            return response;
        }
        throw new ContentPublisherException("Max retries exceeded for " + method + " " + url);
    }

    private String getAuthToken() {
        return tokenStorage.getToken(username).orElseGet(() -> {
            String token = HttpDamUtils.getAuthToken(
                config.getRemoteUser() != null ? config.getRemoteUser() : username,
                config.getRemotePassword(),
                config.getRemoteApiUrl());
            if (token != null) {
                tokenStorage.putToken(username, token);
            }
            return token;
        });
    }

    private String fetchETag(ContentId contentId) {
        try {
            String url = buildContentUrl("contentid", IdUtil.toIdString(contentId));
            String token = getAuthToken();
            HttpDamUtils.WebServiceResponse response = HttpDamUtils.callDataApiWs(
                CONTENT_TYPE, null, HttpDamUtils.WebServiceMethod.GET, url, token, null);
            if (!response.isError()) {
                return response.getHeader("ETag");
            }
        } catch (Exception e) {
            LOG.debug("Cannot fetch ETag for {}: {}", contentId, e.getMessage());
        }
        return null;
    }

    private String extractIdFromLocation(String location) {
        if (location == null) return null;
        // Location typically ends with /content/contentid/{id}
        int lastSlash = location.lastIndexOf('/');
        if (lastSlash >= 0) {
            return location.substring(lastSlash + 1);
        }
        return location;
    }
}
