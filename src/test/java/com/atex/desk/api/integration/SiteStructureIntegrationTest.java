package com.atex.desk.api.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the atex.onecms.structure variant on GET /content/contentid/{id}.
 */
class SiteStructureIntegrationTest extends BaseIntegrationTest {

    private String token;

    @BeforeEach
    void loginBeforeEach() {
        token = loginSysadmin();
    }

    @Test
    @SuppressWarnings("unchecked")
    void structureVariant_returnsTreeWithChildren() throws Exception {
        // Create child page
        String childExtId = "child.page." + System.nanoTime();
        Map<String, Object> child = createPageContent(token, "Child Page", null, childExtId);
        String childId = extractId(child);

        // Create parent page with child as subPage
        String parentExtId = "parent.site." + System.nanoTime();
        Map<String, Object> parent = createPageContent(token, "Parent Site",
            List.of(Map.of("delegationId", "onecms", "key", childId.replace("onecms:", ""))),
            parentExtId);
        String parentVersionedId = extractVersion(parent);

        // GET with structure variant using versioned ID
        HttpResponse<String> response = rawGet(
            "/content/contentid/" + parentVersionedId + "?variant=atex.onecms.structure", token);

        assertEquals(200, response.statusCode());
        Map<String, Object> body = parseJson(response.body());

        // Check contentData has structure fields
        Map<String, Object> aspects = (Map<String, Object>) body.get("aspects");
        assertNotNull(aspects, "aspects should be present");

        Map<String, Object> contentDataAspect = (Map<String, Object>) aspects.get("contentData");
        assertNotNull(contentDataAspect, "contentData aspect should be present");

        Map<String, Object> contentData = (Map<String, Object>) contentDataAspect.get("data");
        assertNotNull(contentData, "contentData.data should be present");

        assertEquals("Parent Site", contentData.get("name"));
        assertNotNull(contentData.get("children"), "children should be present");

        List<Object> children = (List<Object>) contentData.get("children");
        assertEquals(1, children.size(), "should have one child");

        Map<String, Object> childNode = (Map<String, Object>) children.get(0);
        assertEquals("Child Page", childNode.get("name"));
        assertEquals(childExtId, childNode.get("externalId"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void structureVariant_externalIdResolution() throws Exception {
        // Create a page with an external ID
        String extId = "structure.extid." + System.nanoTime();
        Map<String, Object> created = createPageContent(token, "ExtId Page", null, extId);
        assertNotNull(created);

        // GET with structure variant using external ID (no colons)
        HttpResponse<String> response = rawGet(
            "/content/contentid/" + extId + "?variant=atex.onecms.structure", token);

        assertEquals(200, response.statusCode());
        Map<String, Object> body = parseJson(response.body());

        Map<String, Object> aspects = (Map<String, Object>) body.get("aspects");
        Map<String, Object> contentDataAspect = (Map<String, Object>) aspects.get("contentData");
        Map<String, Object> contentData = (Map<String, Object>) contentDataAspect.get("data");

        assertEquals("ExtId Page", contentData.get("name"));
        assertEquals(extId, contentData.get("externalId"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void structureVariant_excludedSitesFiltersChildren() throws Exception {
        // Create two child pages
        String child1ExtId = "keep.child." + System.nanoTime();
        Map<String, Object> child1 = createPageContent(token, "Keep This", null, child1ExtId);
        String child1Id = extractId(child1);

        String child2ExtId = "exclude.child." + System.nanoTime();
        Map<String, Object> child2 = createPageContent(token, "Exclude This", null, child2ExtId);
        String child2Id = extractId(child2);
        String child2VersionedId = extractVersion(child2);

        // Create parent with both children
        Map<String, Object> parent = createPageContent(token, "Parent", List.of(
            Map.of("delegationId", "onecms", "key", child1Id.replace("onecms:", "")),
            Map.of("delegationId", "onecms", "key", child2Id.replace("onecms:", ""))
        ), "parent.excl." + System.nanoTime());
        String parentVersionedId = extractVersion(parent);

        // GET with excludedSites filtering out child2 by its unversioned ID
        HttpResponse<String> response = rawGet(
            "/content/contentid/" + parentVersionedId
                + "?variant=atex.onecms.structure&excludedSites=" + child2Id, token);

        assertEquals(200, response.statusCode());
        Map<String, Object> body = parseJson(response.body());

        Map<String, Object> aspects = (Map<String, Object>) body.get("aspects");
        Map<String, Object> contentDataAspect = (Map<String, Object>) aspects.get("contentData");
        Map<String, Object> contentData = (Map<String, Object>) contentDataAspect.get("data");

        List<Object> children = (List<Object>) contentData.get("children");
        assertEquals(1, children.size(), "excluded child should be filtered out");

        Map<String, Object> remainingChild = (Map<String, Object>) children.get(0);
        assertEquals("Keep This", remainingChild.get("name"));
    }

    @Test
    void structureVariant_notFound_returns404() throws Exception {
        HttpResponse<String> response = rawGet(
            "/content/contentid/nonexistent:bogus:v1?variant=atex.onecms.structure", token);

        assertEquals(404, response.statusCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void structureVariant_emptyChildrenForLeafPage() throws Exception {
        // Create a page with no subPages
        Map<String, Object> leaf = createPageContent(token, "Leaf Page", null,
            "leaf.page." + System.nanoTime());
        String versionedId = extractVersion(leaf);

        HttpResponse<String> response = rawGet(
            "/content/contentid/" + versionedId + "?variant=atex.onecms.structure", token);

        assertEquals(200, response.statusCode());
        Map<String, Object> body = parseJson(response.body());

        Map<String, Object> aspects = (Map<String, Object>) body.get("aspects");
        Map<String, Object> contentDataAspect = (Map<String, Object>) aspects.get("contentData");
        Map<String, Object> contentData = (Map<String, Object>) contentDataAspect.get("data");

        assertEquals("Leaf Page", contentData.get("name"));
        List<Object> children = (List<Object>) contentData.get("children");
        assertNotNull(children);
        assertTrue(children.isEmpty(), "leaf page should have empty children list");
    }

    @Test
    @SuppressWarnings("unchecked")
    void structureVariant_preservesAliasesAspect() throws Exception {
        String extId = "alias.preserved." + System.nanoTime();
        Map<String, Object> created = createPageContent(token, "With Alias", null, extId);
        String versionedId = extractVersion(created);

        HttpResponse<String> response = rawGet(
            "/content/contentid/" + versionedId + "?variant=atex.onecms.structure", token);

        assertEquals(200, response.statusCode());
        Map<String, Object> body = parseJson(response.body());

        Map<String, Object> aspects = (Map<String, Object>) body.get("aspects");

        // atex.Aliases aspect should be preserved from original content
        Map<String, Object> aliasesAspect = (Map<String, Object>) aspects.get("atex.Aliases");
        assertNotNull(aliasesAspect, "atex.Aliases aspect should be preserved");
        Map<String, Object> aliasData = (Map<String, Object>) aliasesAspect.get("data");
        assertNotNull(aliasData);
        Map<String, Object> aliases = (Map<String, Object>) aliasData.get("aliases");
        assertNotNull(aliases);
        assertEquals(extId, aliases.get("externalId"));
    }

    // ---- Helpers ----

    /**
     * Create a page-like content with optional subPages and an external ID alias.
     */
    private Map<String, Object> createPageContent(String token, String name,
                                                   List<Map<String, Object>> subPages,
                                                   String externalId) {
        Map<String, Object> contentData = new LinkedHashMap<>();
        contentData.put("_type", "com.atex.onecms.app.siteengine.PageBean");
        contentData.put("name", name);
        if (subPages != null) {
            contentData.put("subPages", subPages);
        }

        Map<String, Object> aspectData = new LinkedHashMap<>();
        aspectData.put("data", contentData);

        Map<String, Object> aspects = new LinkedHashMap<>();
        aspects.put("contentData", aspectData);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("aspects", aspects);

        if (externalId != null) {
            body.put("operations", List.of(
                Map.of("type", "SetAliasOperation", "namespace", "externalId", "value", externalId)
            ));
        }

        return createContent(token, body);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        return new com.google.gson.Gson().fromJson(json, Map.class);
    }
}
