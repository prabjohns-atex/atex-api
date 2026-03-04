package com.atex.desk.api.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceIntegrationTest extends BaseIntegrationTest {

    private String token;

    @BeforeEach
    void loginBeforeEach() {
        token = loginSysadmin();
    }

    @Test
    void createWorkspaceDraft_returns201() {
        // First create real content
        Map<String, Object> created = createContent(token, articleBody("WS Source", "Lead", "Body"));
        String contentId = extractId(created);

        String wsId = "ws-test-" + System.nanoTime();

        ResponseEntity<Map> response = restClient.put()
            .uri("/content/workspace/{wsId}/contentid/{id}", wsId, contentId)
            .headers(h -> h.addAll(authHeaders(token)))
            .body(articleBody("WS Draft", "Draft Lead", "Draft Body"))
            .retrieve()
            .toEntity(Map.class);

        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getWorkspaceDraft_returns200() {
        // Create real content
        Map<String, Object> created = createContent(token, articleBody("WS Get Source", "Lead", "Body"));
        String contentId = extractId(created);

        String wsId = "ws-get-" + System.nanoTime();

        // Create draft in workspace
        restClient.put()
            .uri("/content/workspace/{wsId}/contentid/{id}", wsId, contentId)
            .headers(h -> h.addAll(authHeaders(token)))
            .body(articleBody("WS Get Draft", "Draft Lead", "Draft Body"))
            .retrieve()
            .body(Map.class);

        // Get draft
        Map<String, Object> draft = restClient.get()
            .uri("/content/workspace/{wsId}/contentid/{id}", wsId, contentId)
            .header("X-Auth-Token", token)
            .retrieve()
            .body(Map.class);

        assertNotNull(draft);
        Map<String, Object> aspects = (Map<String, Object>) draft.get("aspects");
        assertNotNull(aspects);
        assertTrue(aspects.containsKey("contentData"));
    }

    @Test
    void deleteWorkspaceDraft_returns204() {
        // Create real content
        Map<String, Object> created = createContent(token, articleBody("WS Del Source", "Lead", "Body"));
        String contentId = extractId(created);

        String wsId = "ws-del-" + System.nanoTime();

        // Create draft
        restClient.put()
            .uri("/content/workspace/{wsId}/contentid/{id}", wsId, contentId)
            .headers(h -> h.addAll(authHeaders(token)))
            .body(articleBody("WS Del Draft", "Draft Lead", "Draft Body"))
            .retrieve()
            .body(Map.class);

        // Delete draft
        ResponseEntity<Void> response = restClient.delete()
            .uri("/content/workspace/{wsId}/contentid/{id}", wsId, contentId)
            .header("X-Auth-Token", token)
            .retrieve()
            .toBodilessEntity();

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    void getWorkspaceInfo() {
        String wsId = "ws-info-" + System.nanoTime();

        // Create real content and a draft
        Map<String, Object> created = createContent(token, articleBody("WS Info Source", "Lead", "Body"));
        String contentId = extractId(created);

        restClient.put()
            .uri("/content/workspace/{wsId}/contentid/{id}", wsId, contentId)
            .headers(h -> h.addAll(authHeaders(token)))
            .body(articleBody("WS Info Draft", "Draft Lead", "Draft Body"))
            .retrieve()
            .body(Map.class);

        // Get workspace info
        Map response = restClient.get()
            .uri("/content/workspace/{wsId}", wsId)
            .header("X-Auth-Token", token)
            .retrieve()
            .body(Map.class);

        assertNotNull(response);
    }

    @Test
    void clearWorkspace_returns204() {
        String wsId = "ws-clear-" + System.nanoTime();

        // Create content and drafts
        Map<String, Object> created = createContent(token, articleBody("WS Clear Source", "Lead", "Body"));
        String contentId = extractId(created);

        restClient.put()
            .uri("/content/workspace/{wsId}/contentid/{id}", wsId, contentId)
            .headers(h -> h.addAll(authHeaders(token)))
            .body(articleBody("WS Clear Draft", "Draft Lead", "Draft Body"))
            .retrieve()
            .body(Map.class);

        // Clear workspace
        ResponseEntity<Void> response = restClient.delete()
            .uri("/content/workspace/{wsId}", wsId)
            .header("X-Auth-Token", token)
            .retrieve()
            .toBodilessEntity();

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }
}
