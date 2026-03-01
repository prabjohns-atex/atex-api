package com.atex.plugins.layout;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.atex.onecms.app.dam.ws.ContentApiException;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.Status;
import com.atex.onecms.content.Subject;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;

@Service
@ConditionalOnProperty(name = "desk.layout.enabled", havingValue = "true", matchIfMissing = true)
public class LayoutService {

    private static final Logger logger = Logger.getLogger(LayoutService.class.getName());

    private static final long CONFIG_TIMEOUT = 5;
    private static final int MAX_ERRORS = 25;
    private static final int CONNECTION_TIMEOUT = 5 * 1000;

    private static final String LAYOUT_CONFIG_EXTERNAL_ID =
            "atex.plugins.layout.LayoutServerConfiguration";

    private static final Set<String> SKIP_REQUEST_HEADERS = Set.of(
            "content-length", "host", "connection", "transfer-encoding");

    private static final Set<String> SKIP_RESPONSE_HEADERS = Set.of(
            "transfer-encoding", "content-length", "connection");

    private final ContentManager contentManager;

    private final AtomicInteger errorCount = new AtomicInteger();

    private Supplier<LayoutServerConfigurationBean> configStore;

    public LayoutService(ContentManager contentManager) {
        this.contentManager = contentManager;
    }

    @PostConstruct
    void init() {
        this.configStore = Suppliers.memoizeWithExpiration(() -> {
            try {
                return loadConfiguration();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to get layout config", e);
                return new LayoutServerConfigurationBean();
            }
        }, CONFIG_TIMEOUT, TimeUnit.SECONDS);
    }

    private LayoutServerConfigurationBean loadConfiguration() {
        ContentVersionId configId = contentManager.resolve(LAYOUT_CONFIG_EXTERNAL_ID,
                Subject.NOBODY_CALLER);

        if (configId == null) {
            logger.warning("Layout service: Could not resolve config '" + LAYOUT_CONFIG_EXTERNAL_ID + "'");
            return new LayoutServerConfigurationBean();
        }

        ContentResult<LayoutServerConfigurationBean> config =
                contentManager.get(configId, null, LayoutServerConfigurationBean.class,
                        null, Subject.NOBODY_CALLER);

        if (config == null || !config.getStatus().isSuccess()) {
            logger.warning("Layout service: Could not read config content by id '" + configId + "'");
            return new LayoutServerConfigurationBean();
        }

        return config.getContent().getContentData();
    }

    private String encodeContentId(String contentId) {
        if (contentId.startsWith("desk.pe:")) {
            return URLEncoder.encode(contentId, StandardCharsets.UTF_8);
        }
        return contentId;
    }

    private URI buildServerUrl(String path) {
        LayoutServerConfigurationBean config = configStore.get();
        String base = config.getLayoutServer();
        return URI.create(stripTrailingSlash(base) + "/" + path);
    }

    private URI buildNewsRoomServerUrl(String path) {
        LayoutServerConfigurationBean config = configStore.get();
        String base = config.getLayoutNewsRoomServer();
        if (base == null || base.isEmpty()) {
            base = config.getLayoutServer();
        }
        return URI.create(stripTrailingSlash(base) + "/" + path);
    }

    private URI buildUrl(String fieldType, String path) {
        LayoutServerConfigurationBean config = configStore.get();

        if (fieldType.contains("http")) {
            return URI.create(fieldType);
        }

        if (fieldType.equalsIgnoreCase("content")) {
            return URI.create(stripTrailingSlash(config.getPrintServer()) + "/" + path);
        }

        return URI.create(stripTrailingSlash(config.getLayoutServer()) + "/" + path);
    }

    private URI buildUrl(String fieldType, String path, String contentDataType) {
        LayoutServerConfigurationBean config = configStore.get();

        if (fieldType.contains("http")) {
            return URI.create(fieldType);
        }

        if (fieldType.equalsIgnoreCase("content")) {
            return URI.create(stripTrailingSlash(config.getPrintServer()) + "/" + path);
        }

        if (contentDataType.contains("NewsRoom")) {
            String base = config.getLayoutNewsRoomServer();
            if (base == null || base.isEmpty()) {
                base = config.getLayoutServer();
            }
            return URI.create(stripTrailingSlash(base) + "/" + path);
        }

        return URI.create(stripTrailingSlash(config.getLayoutServer()) + "/" + path);
    }

    private String stripTrailingSlash(String url) {
        if (url != null && url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }

    private String decodeUriString(String uri, String radix) {
        int pos = uri.indexOf("&uri=");
        if (pos == -1) {
            return uri;
        }

        String parameters = uri.substring(0, pos);
        String address = uri.substring(pos + 5).replace("%3A", ":");

        if (radix.length() > 0) {
            parameters = radix + parameters;
        }

        return "http://" + address + "/" + parameters;
    }

    private String getQueryParameters(HttpServletRequest request) {
        String queryString = request.getQueryString();
        if (queryString != null && !queryString.isEmpty()) {
            return "&" + queryString;
        }
        return "";
    }

    protected void passOnHeaders(HttpServletRequest request, HttpUriRequest httpRequest) {
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String key = headerNames.nextElement();
                if (SKIP_REQUEST_HEADERS.contains(key.toLowerCase())) {
                    continue;
                }
                String value = request.getHeader(key);
                httpRequest.setHeader(key, value);
            }
        }
    }

    protected HttpHeaders passOnReturnHeaders(HttpResponse httpResponse) {
        HttpHeaders headers = new HttpHeaders();
        Header[] returnedHeaders = httpResponse.getAllHeaders();
        for (Header header : returnedHeaders) {
            if (SKIP_RESPONSE_HEADERS.contains(header.getName().toLowerCase())) {
                continue;
            }
            headers.add(header.getName(), header.getValue());
        }
        return headers;
    }

    protected ResponseEntity<?> executeHttpRequest(HttpUriRequest request) {

        if (logger.isLoggable(Level.FINE)) {
            logger.fine("Request is " + request);
        }

        CloseableHttpResponse autoClose = null;

        try {
            CloseableHttpClient client = getClient();
            CloseableHttpResponse httpResponse = client.execute(request);
            autoClose = httpResponse;

            StatusLine statusLine = httpResponse.getStatusLine();
            if (statusLine == null) {
                throw new NullPointerException("Did not expect to get a null status line");
            }

            HttpEntity entity = httpResponse.getEntity();

            if (logger.isLoggable(Level.FINE)) {
                logger.fine("Status is " + statusLine);
                logger.fine("Entity is " + (entity == null ? "null" : "not null"));
                for (Header h : httpResponse.getAllHeaders()) {
                    logger.fine("Header + " + h.getName() + " = " + h.getValue());
                }
            }

            if (statusLine.getStatusCode() >= 300) {
                if (statusLine.getStatusCode() >= 400) {
                    if (errorCount.addAndGet(1) <= MAX_ERRORS) {
                        logger.log(Level.WARNING, "Request " + request + " failed with "
                                + statusLine.getStatusCode() + ", " + statusLine.getReasonPhrase());
                    }
                }
                EntityUtils.consumeQuietly(entity);
                HttpHeaders headers = passOnReturnHeaders(httpResponse);
                httpResponse.close();
                return ResponseEntity.status(statusLine.getStatusCode())
                        .headers(headers)
                        .build();
            } else {
                errorCount.set(0);

                if (entity != null) {
                    InputStream inputStream = entity.getContent();
                    // Once we have the stream, do not auto-close — close later once the stream has been used.
                    autoClose = null;

                    HttpHeaders headers = passOnReturnHeaders(httpResponse);

                    StreamingResponseBody stream = output -> {
                        try {
                            byte[] buffer = new byte[8192];
                            int bytesRead;
                            while ((bytesRead = inputStream.read(buffer)) != -1) {
                                output.write(buffer, 0, bytesRead);
                            }
                        } catch (Exception e) {
                            logger.log(Level.SEVERE, e.getMessage(), e);
                        } finally {
                            inputStream.close();
                            httpResponse.close();
                        }
                    };

                    return ResponseEntity.ok()
                            .headers(headers)
                            .body(stream);
                } else {
                    httpResponse.close();
                    HttpHeaders headers = passOnReturnHeaders(httpResponse);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .headers(headers)
                            .build();
                }
            }

        } catch (ContentApiException e) {
            throw e;
        } catch (Exception e) {
            logger.log(Level.FINE, e.getMessage(), e);

            if (errorCount.addAndGet(1) <= MAX_ERRORS) {
                if (e.getMessage() != null && e.getMessage().contains("Connection refused")) {
                    logger.log(Level.SEVERE, e.getMessage());
                } else {
                    logger.log(Level.SEVERE, e.getMessage(), e);
                }
            }
            throw new ContentApiException(e.getMessage(),
                    Status.FAILURE.getDetailCode(),
                    Status.FAILURE.getHttpCode(), e);

        } finally {
            if (autoClose != null) {
                try {
                    autoClose.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private CloseableHttpClient getClient() {
        LayoutServerConfigurationBean config = this.configStore.get();
        int timeout = config.getTimeout() * 1000;
        int poolSize = config.getPoolSize();

        RequestConfig defaultRequestConfig = getRequestConfig(timeout);

        if (logger.isLoggable(Level.FINE)) {
            logger.fine("Client Configuration " + config);
        }

        return HttpClients.custom()
                .setDefaultRequestConfig(defaultRequestConfig)
                .setMaxConnTotal(poolSize)
                .setMaxConnPerRoute(poolSize)
                .setRetryHandler(DefaultHttpRequestRetryHandler.INSTANCE)
                .build();
    }

    private RequestConfig getRequestConfig(int timeout) {
        return RequestConfig.custom()
                .setSocketTimeout(timeout)
                .setConnectTimeout(CONNECTION_TIMEOUT)
                .setConnectionRequestTimeout(timeout)
                .build();
    }

    private HttpRequestBase getPerRequestConfig(URI url, String method) {
        HttpRequestBase request;
        switch (method) {
            case HttpPost.METHOD_NAME:
                request = new HttpPost(url);
                break;
            case HttpGet.METHOD_NAME:
                request = new HttpGet(url);
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + method);
        }

        LayoutServerConfigurationBean config = this.configStore.get();
        int timeout = config.getTimeout() * 1000;

        timeout = getModifiedTimeout(config.getSlowUrlRegex(), timeout, 2, url);
        timeout = getModifiedTimeout(config.getvSlowUrlRegex(), timeout, 4, url);

        if (logger.isLoggable(Level.FINE)) {
            logger.fine("Setting timeout to " + timeout + " for " + url);
        }

        request.setConfig(getRequestConfig(timeout));
        return request;
    }

    private int getModifiedTimeout(String slowUrlRegex, int timeout, int modifier, URI url) {
        if (slowUrlRegex != null && !slowUrlRegex.isEmpty()) {
            if (url.getPath().matches(slowUrlRegex)) {
                timeout = timeout * modifier;
            }
        }
        return timeout;
    }

    private String getValueFromJson(String body) {
        String datatype = "contentDataType";
        int pos = body.indexOf(datatype);
        if (pos != -1) {
            String pre = body.substring(pos + datatype.length() + 1);
            int endpos = Math.min(20, pre.length());
            return pre.substring(0, endpos);
        }
        return "";
    }

    // --- Public proxy methods ---

    public ResponseEntity<?> previewURL(String previewId, HttpServletRequest request) {
        URI url;
        int index;
        previewId = decodeUriString(previewId, "layout/getpreview?preview_id=");

        if ((index = previewId.indexOf("-NewsRoom")) != -1) {
            String previewCode = previewId.substring(0, index);
            if (previewCode.contains("http")) {
                url = URI.create(previewCode);
            } else {
                url = buildNewsRoomServerUrl("layout/getpreview?preview_id=" + previewCode);
            }
        } else {
            if (previewId.contains("http")) {
                url = URI.create(previewId);
            } else {
                url = buildServerUrl("layout/getpreview?preview_id=" + previewId);
            }
        }

        logger.fine("previewURL: url was: " + url);

        HttpRequestBase httpGet = getPerRequestConfig(url, HttpGet.METHOD_NAME);
        passOnHeaders(request, httpGet);
        return executeHttpRequest(httpGet);
    }

    public ResponseEntity<?> getLayoutRequest(String fieldType, String contentId,
                                               HttpServletRequest request) {
        URI url;
        String sSub = "layout/" + fieldType + "?content_id=";
        contentId = decodeUriString(contentId, sSub);

        if (contentId.contains("http")) {
            url = URI.create(contentId);
        } else if (contentId.contains("-NewsRoom")) {
            int index = contentId.indexOf("-NewsRoom");
            String scontent = contentId.substring(0, index);
            url = buildUrl(fieldType,
                    "layout/" + fieldType + "?content_id=" + encodeContentId(scontent) + getQueryParameters(request),
                    "NewsRoom");
        } else {
            url = buildUrl(fieldType,
                    "layout/" + fieldType + "?content_id=" + encodeContentId(contentId) + getQueryParameters(request));
        }

        HttpRequestBase httpGet = getPerRequestConfig(url, HttpGet.METHOD_NAME);
        passOnHeaders(request, httpGet);
        return executeHttpRequest(httpGet);
    }

    public ResponseEntity<?> getLayoutRequest(String fieldType, String fieldSubType,
                                               String contentId, HttpServletRequest request) {
        URI url;
        String sSub = "layout/" + fieldType + "/" + fieldSubType + "?content_id=";
        contentId = decodeUriString(contentId, sSub);

        if (contentId.contains("http")) {
            url = URI.create(contentId);
        } else if (contentId.contains("-NewsRoom")) {
            int index = contentId.indexOf("-NewsRoom");
            String scontent = contentId.substring(0, index);
            url = buildUrl(fieldType,
                    "layout/" + fieldType + "/" + fieldSubType + "?content_id=" + encodeContentId(scontent) + getQueryParameters(request),
                    "NewsRoom");
        } else {
            url = buildUrl(fieldType,
                    "layout/" + fieldType + "/" + fieldSubType + "?content_id=" + encodeContentId(contentId) + getQueryParameters(request));
        }

        HttpRequestBase httpGet = getPerRequestConfig(url, HttpGet.METHOD_NAME);
        passOnHeaders(request, httpGet);
        return executeHttpRequest(httpGet);
    }

    public ResponseEntity<?> postLayoutRequest(String body, String fieldType,
                                                String contentId, HttpServletRequest request) {
        String typevalue = getValueFromJson(body);
        URI url = buildUrl(fieldType,
                "layout/" + fieldType + "?content_id=" + encodeContentId(contentId) + getQueryParameters(request),
                typevalue);

        HttpPost httpPost = (HttpPost) getPerRequestConfig(url, HttpPost.METHOD_NAME);
        HttpEntity postEntity = new ByteArrayEntity(body.getBytes(StandardCharsets.UTF_8));
        httpPost.setEntity(postEntity);

        passOnHeaders(request, httpPost);
        return executeHttpRequest(httpPost);
    }

    public ResponseEntity<?> postLayoutRequest(String body, String fieldType,
                                                String fieldSubType, String contentId,
                                                HttpServletRequest request) {
        String typevalue = getValueFromJson(body);
        URI url = buildUrl(fieldType,
                "layout/" + fieldType + "/" + fieldSubType + "?content_id=" + encodeContentId(contentId) + getQueryParameters(request),
                typevalue);

        HttpPost httpPost = (HttpPost) getPerRequestConfig(url, HttpPost.METHOD_NAME);
        HttpEntity postEntity = new ByteArrayEntity(body.getBytes(StandardCharsets.UTF_8));
        httpPost.setEntity(postEntity);

        passOnHeaders(request, httpPost);
        return executeHttpRequest(httpPost);
    }
}
