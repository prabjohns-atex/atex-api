package com.atex.desk.api.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AliasIntegrationTest extends BaseIntegrationTest {

    private String token;

    @BeforeEach
    void loginBeforeEach() {
        token = loginSysadmin();
    }

    @Test
    void externalIdResolve_redirectsToContent() {
        // Create content with an external ID alias via SetAliasOperation
        String aliasValue = "test.alias." + System.nanoTime();
        Map<String, Object> body = articleBodyWithAlias("Alias Test", aliasValue);

        Map<String, Object> created = createContent(token, body);
        String versionedId = extractVersion(created);
        assertNotNull(versionedId);

        // Resolve via external ID — should return 303 or the resolved content
        // Since RestClient may follow redirects, we fetch the final content
        try {
            Map response = restClient.get()
                .uri("/content/externalid/{id}", aliasValue)
                .header("X-Auth-Token", token)
                .retrieve()
                .body(Map.class);

            // If redirect was followed, we get the content
            assertNotNull(response);
        } catch (HttpClientErrorException e) {
            // 303 not followed — that's also valid
            assertEquals(HttpStatus.SEE_OTHER, e.getStatusCode());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void aliasInResponse() {
        String aliasValue = "test.meta.alias." + System.nanoTime();
        Map<String, Object> body = articleBodyWithAlias("Alias Meta Test", aliasValue);

        Map<String, Object> created = createContent(token, body);
        String versionedId = extractVersion(created);

        // Get content and check meta.aliases
        Map<String, Object> content = restClient.get()
            .uri("/content/contentid/{id}", versionedId)
            .header("X-Auth-Token", token)
            .retrieve()
            .body(Map.class);

        Map<String, Object> meta = (Map<String, Object>) content.get("meta");
        assertNotNull(meta, "meta should not be null");
        Map<String, String> aliases = (Map<String, String>) meta.get("aliases");
        assertNotNull(aliases, "aliases should not be null");
        assertEquals(aliasValue, aliases.get("externalId"));
    }

    @Test
    void policyIdFallback() {
        // Create content with a policyId alias
        String policyId = "policy:99." + System.nanoTime();
        Map<String, Object> contentData = new LinkedHashMap<>();
        contentData.put("_type", "atex.onecms.article");
        contentData.put("headline", Map.of("text", "Policy Fallback Test"));

        Map<String, Object> aspectData = new LinkedHashMap<>();
        aspectData.put("data", contentData);

        Map<String, Object> aspects = new LinkedHashMap<>();
        aspects.put("contentData", aspectData);

        List<Map<String, Object>> operations = List.of(
            Map.of("type", "SetAliasOperation", "namespace", "policyId", "value", policyId)
        );

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("aspects", aspects);
        body.put("operations", operations);

        Map<String, Object> created = createContent(token, body);
        assertNotNull(extractVersion(created));

        // Resolve via policy ID — content should be found via alias fallback
        try {
            Map response = restClient.get()
                .uri("/content/contentid/{id}", policyId)
                .header("X-Auth-Token", token)
                .retrieve()
                .body(Map.class);

            // May follow redirect and return content, or return 303
            assertNotNull(response);
        } catch (HttpClientErrorException e) {
            // 303 redirect is valid too
            assertEquals(HttpStatus.SEE_OTHER, e.getStatusCode());
        }
    }

    // ---- Helpers ----

    private Map<String, Object> articleBodyWithAlias(String headline, String aliasValue) {
        Map<String, Object> contentData = new LinkedHashMap<>();
        contentData.put("_type", "atex.onecms.article");
        contentData.put("headline", Map.of("text", headline));
        contentData.put("lead", Map.of("text", "Lead"));
        contentData.put("body", Map.of("text", "Body"));

        Map<String, Object> aspectData = new LinkedHashMap<>();
        aspectData.put("data", contentData);

        Map<String, Object> aspects = new LinkedHashMap<>();
        aspects.put("contentData", aspectData);

        List<Map<String, Object>> operations = List.of(
            Map.of("type", "SetAliasOperation", "namespace", "externalId", "value", aliasValue)
        );

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("aspects", aspects);
        body.put("operations", operations);
        return body;
    }
}
