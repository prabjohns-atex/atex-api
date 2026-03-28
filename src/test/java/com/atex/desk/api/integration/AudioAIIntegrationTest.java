package com.atex.desk.api.integration;

import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for DamAudioAIResource (/dam/audioai).
 */
class AudioAIIntegrationTest extends BaseIntegrationTest {

    private static final Gson GSON = new Gson();
    private String token;

    @BeforeEach
    void setUp() {
        token = loginSysadmin();
    }

    // ---- Search ----

    @Test
    void search_noAuth_returns401() throws Exception {
        HttpResponse<String> resp = rawGet("/dam/audioai/search/onecms:nonexistent", null);
        assertEquals(401, resp.statusCode());
    }

    @Test
    void search_notFound_returns404() throws Exception {
        HttpResponse<String> resp = rawGet("/dam/audioai/search/onecms:no-such-article", token);
        assertEquals(404, resp.statusCode());
    }

    @Test
    void search_afterCreate_findsAudio() throws Exception {
        // Create an article first
        Map<String, Object> article = createContent(token, articleBody("Audio Test", "lead", "body"));
        String articleId = extractId(article);

        // Create audio for this article
        HttpResponse<String> createResp = rawPost(
                "/dam/audioai/create/" + articleId,
                "{\"name\":\"AIAudio - Audio Test\"}",
                token);
        assertEquals(201, createResp.statusCode());

        // Now search for it
        HttpResponse<String> searchResp = rawGet("/dam/audioai/search/" + articleId, token);
        assertEquals(200, searchResp.statusCode());

        // Verify response has content
        Map<String, Object> body = GSON.fromJson(searchResp.body(), Map.class);
        assertNotNull(body.get("id"), "response should have an id");
        assertNotNull(body.get("version"), "response should have a version");

        // Verify cache control is no-store
        String cacheControl = searchResp.headers().firstValue("Cache-Control").orElse("");
        assertTrue(cacheControl.contains("no-store"), "Cache-Control should be no-store, got: " + cacheControl);
    }

    @Test
    void search_withAudioaiPrefix_works() throws Exception {
        // Create an article
        Map<String, Object> article = createContent(token, articleBody("Prefix Test", "lead", "body"));
        String articleId = extractId(article);

        // Create audio
        rawPost("/dam/audioai/create/" + articleId, "{\"name\":\"AIAudio - Prefix Test\"}", token);

        // Search with audioai- prefix (as the reference allows)
        HttpResponse<String> resp = rawGet("/dam/audioai/search/audioai-" + articleId, token);
        assertEquals(200, resp.statusCode());
    }

    // ---- Create ----

    @Test
    void create_noAuth_returns401() throws Exception {
        HttpResponse<String> resp = rawPost("/dam/audioai/create/onecms:fake", "{}", null);
        assertEquals(401, resp.statusCode());
    }

    @Test
    void create_minimalBody_returns201() throws Exception {
        Map<String, Object> article = createContent(token, articleBody("Create Test", "lead", "body"));
        String articleId = extractId(article);

        HttpResponse<String> resp = rawPost(
                "/dam/audioai/create/" + articleId,
                "{}",
                token);
        assertEquals(201, resp.statusCode());

        Map<String, Object> body = GSON.fromJson(resp.body(), Map.class);
        assertNotNull(body.get("id"));
        assertNotNull(body.get("version"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void create_withName_setsHeadlineAndName() throws Exception {
        Map<String, Object> article = createContent(token, articleBody("Named Audio", "lead", "body"));
        String articleId = extractId(article);

        HttpResponse<String> resp = rawPost(
                "/dam/audioai/create/" + articleId,
                "{\"name\":\"AIAudio - Named Audio\"}",
                token);
        assertEquals(201, resp.statusCode());

        Map<String, Object> body = GSON.fromJson(resp.body(), Map.class);
        Map<String, Object> aspects = (Map<String, Object>) body.get("aspects");
        assertNotNull(aspects);
        Map<String, Object> contentData = (Map<String, Object>) aspects.get("contentData");
        assertNotNull(contentData);
        Map<String, Object> data = (Map<String, Object>) contentData.get("data");
        assertNotNull(data);
        assertEquals("AIAudio - Named Audio", data.get("name"));
        assertEquals("AIAudio - Named Audio", data.get("headline"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void create_setsDefaultContentType() throws Exception {
        Map<String, Object> article = createContent(token, articleBody("Default Type", "lead", "body"));
        String articleId = extractId(article);

        HttpResponse<String> resp = rawPost(
                "/dam/audioai/create/" + articleId,
                "{}",
                token);
        assertEquals(201, resp.statusCode());

        Map<String, Object> body = GSON.fromJson(resp.body(), Map.class);
        Map<String, Object> aspects = (Map<String, Object>) body.get("aspects");
        Map<String, Object> contentData = (Map<String, Object>) aspects.get("contentData");
        Map<String, Object> data = (Map<String, Object>) contentData.get("data");
        assertEquals("atex.dam.standard.Audio", data.get("_type"));
        assertEquals("p.DamAudioAI", data.get("inputTemplate"));
        assertEquals("audio", data.get("objectType"));
    }

    @Test
    void create_invalidJson_returns400() throws Exception {
        HttpResponse<String> resp = rawPost(
                "/dam/audioai/create/onecms:fake",
                "not json",
                token);
        // Should be 400 for parse error
        assertTrue(resp.statusCode() >= 400 && resp.statusCode() < 500,
                "Should return 4xx for invalid JSON, got: " + resp.statusCode());
    }

    @Test
    void create_thenSearchByDifferentArticle_returns404() throws Exception {
        Map<String, Object> article1 = createContent(token, articleBody("Article 1", "lead", "body"));
        String articleId1 = extractId(article1);

        Map<String, Object> article2 = createContent(token, articleBody("Article 2", "lead", "body"));
        String articleId2 = extractId(article2);

        // Create audio for article1
        rawPost("/dam/audioai/create/" + articleId1, "{\"name\":\"Audio 1\"}", token);

        // Search for article2 — should not find it
        HttpResponse<String> resp = rawGet("/dam/audioai/search/" + articleId2, token);
        assertEquals(404, resp.statusCode());
    }
}
