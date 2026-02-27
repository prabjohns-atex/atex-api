package com.atex.onecms.app.dam.util;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * HTTP utility methods for DAM file operations.
 * Provides methods for file upload/download and REST API calls.
 */
public class HttpDamUtils {

    private static final Logger LOG = LoggerFactory.getLogger(HttpDamUtils.class);

    private static final int CONNECTION_TIMEOUT = 20_000;
    private static final int SOCKET_TIMEOUT = 60_000;

    public enum WebServiceMethod {
        GET, POST, PUT, DELETE, POST_FILE, PUT_FILE
    }

    /**
     * Response from a web service call.
     */
    public static class WebServiceResponse {
        private final String body;
        private final boolean error;
        private final String errorMessage;
        private final Map<String, String> headers;

        public WebServiceResponse(String body, boolean error, String errorMessage, Map<String, String> headers) {
            this.body = body;
            this.error = error;
            this.errorMessage = errorMessage;
            this.headers = headers != null ? headers : new HashMap<>();
        }

        public String getBody() { return body; }
        public boolean isError() { return error; }
        public String getErrorMessage() { return errorMessage; }
        public Map<String, String> getHeaders() { return headers; }
        public String getHeader(String name) { return headers.get(name); }
    }

    /**
     * Read binary content from a URL with authentication.
     */
    public static ByteArrayOutputStream readBinaryContentFromURL(String url, String mediaType, String authToken) {
        return readBinaryContentFromURL(url, mediaType, msg -> setAuthToken(msg, authToken));
    }

    /**
     * Read binary content from a URL with custom header configuration.
     */
    public static ByteArrayOutputStream readBinaryContentFromURL(String url, String mediaType,
                                                                  Consumer<HttpUriRequest> messageConsumer) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        RequestConfig config = RequestConfig.custom()
            .setConnectTimeout(CONNECTION_TIMEOUT)
            .setSocketTimeout(SOCKET_TIMEOUT)
            .build();

        try (CloseableHttpClient client = HttpClients.custom()
                .setDefaultRequestConfig(config)
                .build()) {

            HttpGet request = new HttpGet(url);
            if (mediaType != null) {
                request.setHeader("Accept", mediaType);
            }
            if (messageConsumer != null) {
                messageConsumer.accept(request);
            }

            try (CloseableHttpResponse response = client.execute(request)) {
                int status = response.getStatusLine().getStatusCode();
                if (status >= 200 && status < 300) {
                    HttpEntity entity = response.getEntity();
                    if (entity != null) {
                        entity.writeTo(baos);
                    }
                } else {
                    LOG.warn("HTTP {} reading binary from URL: {}", status, url);
                }
            }
        } catch (IOException e) {
            LOG.error("Failed to read binary content from URL: {}", url, e);
        }
        return baos;
    }

    /**
     * Send a stream to the file service.
     */
    public static WebServiceResponse sendStreamToFileService(String mimeType, InputStream is,
                                                              String url, String authToken) {
        return sendStreamToFileService(mimeType, is, url, msg -> setAuthToken(msg, authToken));
    }

    /**
     * Send a stream to the file service with custom header configuration.
     */
    public static WebServiceResponse sendStreamToFileService(String mimeType, InputStream is,
                                                              String url,
                                                              Consumer<HttpUriRequest> messageConsumer) {
        RequestConfig config = RequestConfig.custom()
            .setConnectTimeout(CONNECTION_TIMEOUT)
            .setSocketTimeout(SOCKET_TIMEOUT)
            .build();

        try (CloseableHttpClient client = HttpClients.custom()
                .setDefaultRequestConfig(config)
                .build()) {

            HttpPost post = new HttpPost(url);
            InputStreamEntity entity = new InputStreamEntity(is);
            entity.setContentType(mimeType);
            post.setEntity(entity);

            if (messageConsumer != null) {
                messageConsumer.accept(post);
            }

            try (CloseableHttpResponse response = client.execute(post)) {
                return processResponse(response);
            }
        } catch (IOException e) {
            LOG.error("Failed to send stream to file service: {}", url, e);
            return new WebServiceResponse(null, true, e.getMessage(), null);
        }
    }

    /**
     * Send binary content to the file service.
     */
    public static WebServiceResponse sendBinaryToFileService(String contentType, String filePath,
                                                              String url, String authToken) {
        return sendBinaryToFileService(contentType, filePath, url, msg -> setAuthToken(msg, authToken));
    }

    /**
     * Send binary content to the file service with custom header configuration.
     */
    public static WebServiceResponse sendBinaryToFileService(String contentType, String filePath,
                                                              String url,
                                                              Consumer<HttpUriRequest> messageConsumer) {
        RequestConfig config = RequestConfig.custom()
            .setConnectTimeout(CONNECTION_TIMEOUT)
            .setSocketTimeout(SOCKET_TIMEOUT)
            .build();

        try (CloseableHttpClient client = HttpClients.custom()
                .setDefaultRequestConfig(config)
                .build()) {

            HttpPost post = new HttpPost(url);
            post.setHeader("Content-Type", contentType);
            post.setHeader("X-File-Path", filePath);

            if (messageConsumer != null) {
                messageConsumer.accept(post);
            }

            try (CloseableHttpResponse response = client.execute(post)) {
                return processResponse(response);
            }
        } catch (IOException e) {
            LOG.error("Failed to send binary to file service: {}", url, e);
            return new WebServiceResponse(null, true, e.getMessage(), null);
        }
    }

    /**
     * General-purpose REST API call.
     */
    public static WebServiceResponse callDataApiWs(String contentType, String jsonRequest,
                                                    WebServiceMethod method, String url) {
        return callDataApiWs(contentType, jsonRequest, method, url, null, null);
    }

    /**
     * General-purpose REST API call with auth and ETag.
     */
    public static WebServiceResponse callDataApiWs(String contentType, String jsonRequest,
                                                    WebServiceMethod method, String url,
                                                    String authToken, String etag) {
        RequestConfig config = RequestConfig.custom()
            .setConnectTimeout(CONNECTION_TIMEOUT)
            .setSocketTimeout(SOCKET_TIMEOUT)
            .build();

        try (CloseableHttpClient client = HttpClients.custom()
                .setDefaultRequestConfig(config)
                .build()) {

            HttpUriRequest request = buildRequest(method, url, contentType, jsonRequest);
            if (authToken != null) {
                setAuthToken(request, authToken);
            }
            if (etag != null) {
                request.setHeader("If-Match", etag);
            }

            try (CloseableHttpResponse response = client.execute(request)) {
                return processResponse(response);
            }
        } catch (IOException e) {
            LOG.error("Failed to call data API: {} {}", method, url, e);
            return new WebServiceResponse(null, true, e.getMessage(), null);
        }
    }

    /**
     * Get an auth token by logging in.
     */
    public static String getAuthToken(String username, String password, String dataApiUrl) {
        String url = dataApiUrl + "/security/token";
        String json = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        WebServiceResponse response = callDataApiWs("application/json", json,
            WebServiceMethod.POST, url);
        if (!response.isError() && response.getBody() != null) {
            // Parse token from response - simple extraction
            String body = response.getBody();
            int tokenIdx = body.indexOf("\"token\"");
            if (tokenIdx >= 0) {
                int start = body.indexOf('"', tokenIdx + 7);
                int end = body.indexOf('"', start + 1);
                if (start >= 0 && end >= 0) {
                    return body.substring(start + 1, end);
                }
            }
        }
        return null;
    }

    /**
     * Set the auth token header on an HTTP request.
     */
    public static void setAuthToken(HttpUriRequest request, String authToken) {
        if (authToken != null) {
            request.setHeader("X-Auth-Token", authToken);
        }
    }

    private static HttpUriRequest buildRequest(WebServiceMethod method, String url,
                                                String contentType, String body) {
        switch (method) {
            case GET:
                return new HttpGet(url);
            case DELETE:
                return new HttpDelete(url);
            case PUT:
            case PUT_FILE: {
                HttpPut put = new HttpPut(url);
                if (body != null) {
                    StringEntity entity = new StringEntity(body, StandardCharsets.UTF_8);
                    entity.setContentType(contentType);
                    put.setEntity(entity);
                }
                return put;
            }
            case POST:
            case POST_FILE:
            default: {
                HttpPost post = new HttpPost(url);
                if (body != null) {
                    StringEntity entity = new StringEntity(body, StandardCharsets.UTF_8);
                    entity.setContentType(contentType);
                    post.setEntity(entity);
                }
                return post;
            }
        }
    }

    private static WebServiceResponse processResponse(CloseableHttpResponse response) throws IOException {
        int status = response.getStatusLine().getStatusCode();
        String body = null;
        HttpEntity entity = response.getEntity();
        if (entity != null) {
            body = EntityUtils.toString(entity, StandardCharsets.UTF_8);
        }

        Map<String, String> headers = new HashMap<>();
        for (Header h : response.getAllHeaders()) {
            headers.put(h.getName(), h.getValue());
        }

        boolean error = status >= 400;
        String errorMessage = error ? "HTTP " + status + ": " + body : null;

        // Capture Location header for uploads (201 Created)
        Header locationHeader = response.getFirstHeader("Location");
        if (locationHeader != null) {
            headers.put("Location", locationHeader.getValue());
        }

        // Capture ETag
        Header etagHeader = response.getFirstHeader("ETag");
        if (etagHeader != null) {
            headers.put("ETag", etagHeader.getValue());
        }

        return new WebServiceResponse(body, error, errorMessage, headers);
    }
}
