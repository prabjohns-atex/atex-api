package com.atex.desk.api.integration;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.MySQLContainer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared base class for integration tests.
 * Uses a single shared MySQL Testcontainer across all test classes (singleton pattern).
 * The container starts once and stays alive for the entire test suite.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "desk.security.login.rate-limit=10000")
@ActiveProfiles("itest")
public abstract class BaseIntegrationTest {

    /** Shared singleton container — started once, reused across all test classes. */
    static final MySQLContainer<?> mysql;

    static {
        mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("desk")
            .withUsername("desk")
            .withPassword("desk");
        mysql.start();
    }

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @LocalServerPort
    int port;

    protected RestClient restClient;

    /** Raw JDK HttpClient that doesn't interfere with 401 WWW-Authenticate headers. */
    protected HttpClient rawClient;

    @BeforeEach
    void setUpRestClient() {
        restClient = RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .build();
        rawClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    }

    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    // ---- Auth helpers ----

    protected String login(String username, String password) {
        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
            .uri("/security/token")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("username", username, "password", password))
            .retrieve()
            .body(Map.class);
        return (String) response.get("token");
    }

    protected String loginSysadmin() {
        return login("sysadmin", "sysadmin");
    }

    protected HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Auth-Token", token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    // ---- Raw HTTP helpers (bypass Jetty client 401 interception) ----

    protected HttpResponse<String> rawRequest(String method, String path, String body, Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl() + path));

        if (body != null) {
            builder.method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }

        builder.header("Content-Type", "application/json");
        if (headers != null) {
            headers.forEach(builder::header);
        }

        return rawClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> rawGet(String path, String token) throws Exception {
        return rawRequest("GET", path, null, token != null ? Map.of("X-Auth-Token", token) : null);
    }

    protected HttpResponse<byte[]> rawGetBytes(String path, Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl() + path))
            .GET();
        if (headers != null) {
            headers.forEach(builder::header);
        }
        return rawClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    protected HttpResponse<String> rawPost(String path, String body, String token) throws Exception {
        Map<String, String> headers = token != null ? Map.of("X-Auth-Token", token) : null;
        return rawRequest("POST", path, body, headers);
    }

    protected HttpResponse<String> rawPut(String path, String body, String token, String ifMatch) throws Exception {
        Map<String, String> headers = new LinkedHashMap<>();
        if (token != null) headers.put("X-Auth-Token", token);
        if (ifMatch != null) headers.put("If-Match", ifMatch);
        return rawRequest("PUT", path, body, headers);
    }

    protected HttpResponse<String> rawDelete(String path, String token, String ifMatch) throws Exception {
        Map<String, String> headers = new LinkedHashMap<>();
        if (token != null) headers.put("X-Auth-Token", token);
        if (ifMatch != null) headers.put("If-Match", ifMatch);
        return rawRequest("DELETE", path, null, headers);
    }

    // ---- Content factory helpers ----

    protected Map<String, Object> articleBody(String headline, String lead, String body) {
        Map<String, Object> contentData = new LinkedHashMap<>();
        contentData.put("_type", "atex.onecms.article");
        contentData.put("headline", Map.of("text", headline));
        contentData.put("lead", Map.of("text", lead));
        contentData.put("body", Map.of("text", body));

        Map<String, Object> aspectData = new LinkedHashMap<>();
        aspectData.put("data", contentData);

        Map<String, Object> aspects = new LinkedHashMap<>();
        aspects.put("contentData", aspectData);

        return Map.of("aspects", aspects);
    }

    protected Map<String, Object> imageBody(String title, String description) {
        Map<String, Object> contentData = new LinkedHashMap<>();
        contentData.put("_type", "atex.onecms.image");
        contentData.put("title", title);
        contentData.put("description", description);

        Map<String, Object> aspectData = new LinkedHashMap<>();
        aspectData.put("data", contentData);

        Map<String, Object> aspects = new LinkedHashMap<>();
        aspects.put("contentData", aspectData);

        return Map.of("aspects", aspects);
    }

    protected Map<String, Object> collectionBody(String headline) {
        Map<String, Object> contentData = new LinkedHashMap<>();
        contentData.put("_type", "atex.dam.standard.Collection");
        contentData.put("headline", Map.of("text", headline));

        Map<String, Object> aspectData = new LinkedHashMap<>();
        aspectData.put("data", contentData);

        Map<String, Object> aspects = new LinkedHashMap<>();
        aspects.put("contentData", aspectData);

        return Map.of("aspects", aspects);
    }

    protected Map<String, Object> audioBody(String description) {
        Map<String, Object> contentData = new LinkedHashMap<>();
        contentData.put("_type", "atex.dam.standard.Audio");
        contentData.put("description", description);

        Map<String, Object> aspectData = new LinkedHashMap<>();
        aspectData.put("data", contentData);

        Map<String, Object> aspects = new LinkedHashMap<>();
        aspects.put("contentData", aspectData);

        return Map.of("aspects", aspects);
    }

    protected Map<String, Object> videoBody(String headline) {
        Map<String, Object> contentData = new LinkedHashMap<>();
        contentData.put("_type", "atex.dam.standard.Video");
        contentData.put("headline", Map.of("text", headline));

        Map<String, Object> aspectData = new LinkedHashMap<>();
        aspectData.put("data", contentData);

        Map<String, Object> aspects = new LinkedHashMap<>();
        aspects.put("contentData", aspectData);

        return Map.of("aspects", aspects);
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> createContent(String token, Map<String, Object> body) {
        return restClient.post()
            .uri("/content")
            .headers(h -> h.addAll(authHeaders(token)))
            .body(body)
            .retrieve()
            .body(Map.class);
    }

    protected String extractId(Map<String, Object> createResponse) {
        return (String) createResponse.get("id");
    }

    protected String extractVersion(Map<String, Object> createResponse) {
        return (String) createResponse.get("version");
    }

    protected String toJson(Map<String, Object> map) {
        return new com.google.gson.Gson().toJson(map);
    }
}
