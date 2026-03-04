package com.atex.desk.api.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PrincipalsIntegrationTest extends BaseIntegrationTest {

    private String token;

    @BeforeEach
    void loginBeforeEach() {
        token = loginSysadmin();
    }

    @Test
    @SuppressWarnings("unchecked")
    void getMe_returnsUserDetails() {
        Map<String, Object> response = restClient.get()
            .uri("/principals/users/me")
            .header("X-Auth-Token", token)
            .retrieve()
            .body(Map.class);

        assertNotNull(response);
        assertEquals("sysadmin", response.get("loginName"));
        assertTrue((Boolean) response.get("cmUser"));
        assertFalse((Boolean) response.get("ldapUser"));
        assertFalse((Boolean) response.get("remoteUser"));
        assertNotNull(response.get("groups"));
        assertNotNull(response.get("userData"));
        assertNotNull(response.get("workingSites"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getUserById_returnsUser() {
        Map<String, Object> response = restClient.get()
            .uri("/principals/users/{userId}", "sysadmin")
            .header("X-Auth-Token", token)
            .retrieve()
            .body(Map.class);

        assertNotNull(response);
        assertEquals("sysadmin", response.get("loginName"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listUsers_returnsCorrectFormat() {
        List<Map<String, Object>> response = restClient.get()
            .uri("/principals/users")
            .header("X-Auth-Token", token)
            .retrieve()
            .body(List.class);

        assertNotNull(response);
        assertFalse(response.isEmpty());

        Map<String, Object> firstUser = response.get(0);
        // Reference format fields
        assertNotNull(firstUser.get("id"), "should have 'id'");
        assertNotNull(firstUser.get("name"), "should have 'name'");
        assertNotNull(firstUser.get("principalId"), "should have 'principalId'");
        assertEquals("user", firstUser.get("type"));
        assertNotNull(firstUser.get("cmUser"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void createAndListGroups() {
        // Create a group
        String groupName = "test-group-" + System.nanoTime();
        Map<String, Object> createResponse = restClient.post()
            .uri("/principals/groups")
            .headers(h -> h.addAll(authHeaders(token)))
            .body(Map.of("name", groupName))
            .retrieve()
            .body(Map.class);

        assertNotNull(createResponse);
        assertNotNull(createResponse.get("groupId"));
        assertEquals(groupName, createResponse.get("name"));

        // List groups — should include our new group
        List<Map<String, Object>> groups = restClient.get()
            .uri("/principals/groups")
            .header("X-Auth-Token", token)
            .retrieve()
            .body(List.class);

        assertNotNull(groups);
        boolean found = groups.stream()
            .anyMatch(g -> groupName.equals(g.get("name")));
        assertTrue(found, "Created group should appear in list");
    }
}
