package com.atex.desk.api.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the MyType permissions endpoint (Increment 39).
 *
 * GET /dam/mytype/ping        — health check
 * GET /dam/mytype/permissions  — flattened user permissions
 *
 * Enables config system (disabled in itest profile by default) so that
 * the default mytype.general.permissions.json5 is loaded from classpath.
 */
@TestPropertySource(properties = "desk.config.enabled=true")
class MyTypePermissionsIntegrationTest extends BaseIntegrationTest {

    private String token;

    @BeforeEach
    void loginBeforeEach() {
        token = loginSysadmin();
    }

    // ---- Ping ----

    @Test
    void ping_returnsOk() throws Exception {
        HttpResponse<String> resp = rawGet("/dam/mytype/ping", token);
        assertEquals(200, resp.statusCode());
        assertEquals("mytype connector service available", resp.body());
    }

    // ---- Permissions ----

    @Test
    @SuppressWarnings("unchecked")
    void permissions_returnsValidStructure() throws Exception {
        HttpResponse<String> resp = rawGet("/dam/mytype/permissions", token);
        assertEquals(200, resp.statusCode());

        Map<String, Object> body = new com.google.gson.Gson().fromJson(resp.body(), Map.class);
        assertNotNull(body);

        // Schema version
        assertEquals("1.0", body.get("schemaVersion"), "schemaVersion should be 1.0");

        // User object
        Map<String, Object> user = (Map<String, Object>) body.get("user");
        assertNotNull(user, "response should have 'user' field");
        assertNotNull(user.get("id"), "user should have 'id'");
        assertTrue(user.get("id").toString().startsWith("user-"), "user id should start with 'user-'");
        assertNotNull(user.get("groups"), "user should have 'groups' list");

        // Permissions array
        List<Map<String, Object>> permissions = (List<Map<String, Object>>) body.get("permissions");
        assertNotNull(permissions, "response should have 'permissions' array");
    }

    @Test
    @SuppressWarnings("unchecked")
    void permissions_containsWildcardGroupPermissions() throws Exception {
        // The default config has a wildcard (*) group with deny rules.
        // Even a user with no group memberships should get the wildcard permissions.
        HttpResponse<String> resp = rawGet("/dam/mytype/permissions", token);
        assertEquals(200, resp.statusCode());

        Map<String, Object> body = new com.google.gson.Gson().fromJson(resp.body(), Map.class);
        List<Map<String, Object>> permissions = (List<Map<String, Object>>) body.get("permissions");

        // Should have at least the wildcard group deny (operation:publish for deleted status)
        boolean hasWildcardDeny = permissions.stream()
            .anyMatch(p -> "*".equals(((Map<String, Object>) p.get("source")).get("group"))
                           && "deny".equals(p.get("effect")));
        assertTrue(hasWildcardDeny, "should include permissions from wildcard (*) group");
    }

    @Test
    @SuppressWarnings("unchecked")
    void permissions_entryHasCorrectFormat() throws Exception {
        HttpResponse<String> resp = rawGet("/dam/mytype/permissions", token);
        assertEquals(200, resp.statusCode());

        Map<String, Object> body = new com.google.gson.Gson().fromJson(resp.body(), Map.class);
        List<Map<String, Object>> permissions = (List<Map<String, Object>>) body.get("permissions");

        // At minimum the wildcard group deny should exist
        assertFalse(permissions.isEmpty(), "permissions should not be empty (wildcard group has rules)");

        Map<String, Object> first = permissions.get(0);
        assertNotNull(first.get("permissionId"), "entry should have 'permissionId'");
        assertNotNull(first.get("effect"), "entry should have 'effect'");
        assertTrue("grant".equals(first.get("effect")) || "deny".equals(first.get("effect")),
                   "effect should be 'grant' or 'deny'");
        assertNotNull(first.get("conditions"), "entry should have 'conditions' array");
        assertTrue(first.get("conditions") instanceof List, "conditions should be a list");
        assertNotNull(first.get("source"), "entry should have 'source' object");
        Map<String, Object> source = (Map<String, Object>) first.get("source");
        assertNotNull(source.get("group"), "source should have 'group' field");
    }

    @Test
    @SuppressWarnings("unchecked")
    void permissions_withGroupMembership_includesGroupPermissions() throws Exception {
        // Create a group "Editors" matching a group in the default permissions config
        HttpResponse<String> createResp = rawRequest("POST", "/principals/groups",
                "{\"name\":\"Editors\"}", Map.of("X-Auth-Token", token, "Content-Type", "application/json"));
        assertTrue(createResp.statusCode() >= 200 && createResp.statusCode() < 300,
                "group creation should succeed (status " + createResp.statusCode() + "): " + createResp.body());

        Map<String, Object> createBody = new com.google.gson.Gson().fromJson(createResp.body(), Map.class);
        int groupId = Integer.parseInt(createBody.get("groupId").toString());

        // Add sysadmin (principalId=98) to the Editors group
        HttpResponse<String> addResp = rawRequest("POST", "/principals/groups/" + groupId + "/members",
                "{\"principalId\":\"98\"}", Map.of("X-Auth-Token", token, "Content-Type", "application/json"));
        assertTrue(addResp.statusCode() >= 200 && addResp.statusCode() < 300,
                "member add should succeed (status " + addResp.statusCode() + "): " + addResp.body());

        try {
            // Now fetch permissions — should include both Editors and wildcard (*) permissions
            HttpResponse<String> resp = rawGet("/dam/mytype/permissions", token);
            assertEquals(200, resp.statusCode());

            Map<String, Object> body = new com.google.gson.Gson().fromJson(resp.body(), Map.class);

            // User groups should include "Editors"
            Map<String, Object> user = (Map<String, Object>) body.get("user");
            List<String> groups = (List<String>) user.get("groups");
            assertTrue(groups.contains("Editors"), "user groups should include 'Editors', got: " + groups);

            // Permissions should have entries from Editors group
            List<Map<String, Object>> permissions = (List<Map<String, Object>>) body.get("permissions");
            boolean hasEditorsEntry = permissions.stream()
                .anyMatch(p -> "Editors".equals(((Map<String, Object>) p.get("source")).get("group")));
            assertTrue(hasEditorsEntry, "should include permissions from Editors group");

            // Should also still have wildcard group permissions
            boolean hasWildcard = permissions.stream()
                .anyMatch(p -> "*".equals(((Map<String, Object>) p.get("source")).get("group")));
            assertTrue(hasWildcard, "should still include wildcard (*) group permissions");
        } finally {
            // Clean up: remove membership and delete group
            try {
                rawDelete("/principals/groups/" + groupId + "/members/98", token, null);
            } catch (Exception ignored) {}
            try {
                rawDelete("/principals/groups/" + groupId, token, null);
            } catch (Exception ignored) {}
        }
    }

    @Test
    void permissions_withoutAuth_returns401() throws Exception {
        HttpResponse<String> resp = rawGet("/dam/mytype/permissions", null);
        assertEquals(401, resp.statusCode());
    }

    @Test
    void ping_withoutAuth_returns401() throws Exception {
        // ping is under /dam/* which requires auth
        HttpResponse<String> resp = rawGet("/dam/mytype/ping", null);
        assertEquals(401, resp.statusCode());
    }

    @Test
    void permissions_hasCacheControlNoStore() throws Exception {
        HttpResponse<String> resp = rawGet("/dam/mytype/permissions", token);
        assertEquals(200, resp.statusCode());

        String cacheControl = resp.headers().firstValue("Cache-Control").orElse("");
        assertTrue(cacheControl.contains("no-store"),
                   "permissions response should have Cache-Control: no-store, got: " + cacheControl);
    }

    @Test
    @SuppressWarnings("unchecked")
    void permissions_wildcardGroupConditionsMatchConfig() throws Exception {
        // The default config has wildcard (*) group with deny: operation:publish
        // with condition: "aspects/atex.WebContentStatus/data/status/id:deleted"
        HttpResponse<String> resp = rawGet("/dam/mytype/permissions", token);
        assertEquals(200, resp.statusCode());

        Map<String, Object> body = new com.google.gson.Gson().fromJson(resp.body(), Map.class);
        List<Map<String, Object>> permissions = (List<Map<String, Object>>) body.get("permissions");

        // Find the wildcard group deny for operation:publish
        Map<String, Object> wildcardPublishDeny = permissions.stream()
            .filter(p -> "*".equals(((Map<String, Object>) p.get("source")).get("group"))
                         && "deny".equals(p.get("effect"))
                         && "operation:publish".equals(p.get("permissionId")))
            .findFirst()
            .orElse(null);

        assertNotNull(wildcardPublishDeny, "should have wildcard deny for operation:publish");

        List<String> conditions = (List<String>) wildcardPublishDeny.get("conditions");
        assertNotNull(conditions, "conditions should not be null");
        assertFalse(conditions.isEmpty(), "conditions should not be empty");
        assertTrue(conditions.stream().anyMatch(c -> c.contains("atex.WebContentStatus")),
                   "conditions should reference atex.WebContentStatus");
    }

    @Test
    @SuppressWarnings("unchecked")
    void permissions_noGroupMembership_onlyWildcardPermissions() throws Exception {
        // sysadmin user (principalId=98) with no group memberships should only get wildcard (*) permissions
        HttpResponse<String> resp = rawGet("/dam/mytype/permissions", token);
        assertEquals(200, resp.statusCode());

        Map<String, Object> body = new com.google.gson.Gson().fromJson(resp.body(), Map.class);
        List<Map<String, Object>> permissions = (List<Map<String, Object>>) body.get("permissions");

        // All permissions should come from the wildcard (*) group
        boolean allWildcard = permissions.stream()
            .allMatch(p -> "*".equals(((Map<String, Object>) p.get("source")).get("group")));
        assertTrue(allWildcard, "user with no group memberships should only get wildcard permissions");

        // User groups list should be empty
        Map<String, Object> user = (Map<String, Object>) body.get("user");
        List<String> groups = (List<String>) user.get("groups");
        assertTrue(groups.isEmpty(), "user with no group memberships should have empty groups list");
    }
}
