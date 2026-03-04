package com.atex.desk.api.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SecurityIntegrationTest extends BaseIntegrationTest {

    @Test
    void login_validCredentials_returnsToken() {
        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
            .uri("/security/token")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("username", "sysadmin", "password", "sysadmin"))
            .retrieve()
            .body(Map.class);

        assertNotNull(response);
        assertNotNull(response.get("token"));
        assertEquals("sysadmin", response.get("userId"));
        assertNotNull(response.get("expireTime"));
    }

    @Test
    void login_invalidCredentials_returns401() throws Exception {
        HttpResponse<String> response = rawPost(
            "/security/token",
            "{\"username\":\"sysadmin\",\"password\":\"wrongpassword\"}",
            null);

        assertEquals(401, response.statusCode());
        assertTrue(response.body().contains("statusCode"));
        assertTrue(response.body().contains("40100"));
    }

    @Test
    void validateToken_returnsInfo() {
        String token = loginSysadmin();

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.get()
            .uri("/security/token")
            .header("X-Auth-Token", token)
            .retrieve()
            .body(Map.class);

        assertNotNull(response);
        assertEquals(token, response.get("token"));
        assertEquals("sysadmin", response.get("userId"));
    }

    @Test
    void protectedEndpoint_noToken_returns401() throws Exception {
        HttpResponse<String> response = rawGet("/content/contentid/onecms:nonexistent", null);
        assertEquals(401, response.statusCode());
    }

    @Test
    void protectedEndpoint_invalidToken_returns401() throws Exception {
        HttpResponse<String> response = rawGet("/content/contentid/onecms:nonexistent", "invalid.jwt.token");
        assertEquals(401, response.statusCode());
    }
}
