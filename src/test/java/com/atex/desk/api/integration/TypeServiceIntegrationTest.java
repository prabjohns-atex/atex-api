package com.atex.desk.api.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for GET /content/type/{typeName}.
 */
class TypeServiceIntegrationTest extends BaseIntegrationTest {

    private String token;

    @BeforeEach
    void loginBeforeEach() {
        token = loginSysadmin();
    }

    @Test
    @SuppressWarnings("unchecked")
    void articleType_returnsCorrectSchema() throws Exception {
        HttpResponse<String> response = rawGet("/content/type/atex.onecms.article", token);

        assertEquals(200, response.statusCode());
        Map<String, Object> body = parseJson(response.body());

        assertEquals("com.polopoly.model.ModelTypeBean", body.get("_type"));
        assertEquals("atex.onecms.article", body.get("typeName"));
        assertEquals("bean", body.get("typeClass"));
        assertEquals("desk-api", body.get("generator"));
        assertEquals("true", body.get("addAll"));
        assertNotNull(body.get("beanClass"));
        assertTrue(body.get("beanClass").toString().contains("OneArticleBean"));

        List<Map<String, Object>> attributes = (List<Map<String, Object>>) body.get("attributes");
        assertNotNull(attributes);
        assertFalse(attributes.isEmpty());

        // Check StructuredText fields are correctly typed
        assertAttributeType(attributes, "headline", "com.atex.plugins.structured.text.StructuredText");
        assertAttributeType(attributes, "lead", "com.atex.plugins.structured.text.StructuredText");
        assertAttributeType(attributes, "body", "com.atex.plugins.structured.text.StructuredText");

        // Check other field types
        assertAttributeType(attributes, "byline", "java.lang.String");
        assertAttributeType(attributes, "premiumContent", "boolean");
        assertAttributeType(attributes, "priority", "int");

        // Check list types include generics
        Map<String, Object> imagesAttr = findAttribute(attributes, "images");
        assertNotNull(imagesAttr, "images attribute should exist");
        assertTrue(imagesAttr.get("typeName").toString().contains("java.util.List"),
            "images should be a List type");
        assertTrue(imagesAttr.get("typeName").toString().contains("ContentId"),
            "images should be List<ContentId>");

        // Check modifiers (3 = READ|WRITE)
        assertEquals(3L, ((Number) imagesAttr.get("modifiers")).longValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void articleType_hasMetadataAttributes() throws Exception {
        HttpResponse<String> response = rawGet("/content/type/atex.onecms.article", token);
        assertEquals(200, response.statusCode());

        Map<String, Object> body = parseJson(response.body());
        List<Map<String, Object>> attributes = (List<Map<String, Object>>) body.get("attributes");

        // p.beanClass (modifiers=9 = READ|STATIC)
        Map<String, Object> beanClassAttr = findAttribute(attributes, "p.beanClass");
        assertNotNull(beanClassAttr, "p.beanClass metadata attribute should exist");
        assertEquals("java.lang.String", beanClassAttr.get("typeName"));
        assertEquals(9L, ((Number) beanClassAttr.get("modifiers")).longValue());
        assertTrue(beanClassAttr.get("value").toString().contains("OneArticleBean"));

        // p.mt
        Map<String, Object> mtAttr = findAttribute(attributes, "p.mt");
        assertNotNull(mtAttr, "p.mt metadata attribute should exist");
        assertEquals("atex.onecms.article", mtAttr.get("value"));

        // _data (modifiers=257 = READ|TRANSIENT)
        Map<String, Object> dataAttr = findAttribute(attributes, "_data");
        assertNotNull(dataAttr, "_data metadata attribute should exist");
        assertEquals(257L, ((Number) dataAttr.get("modifiers")).longValue());
    }

    @Test
    void wfContentStatus_returnsSchema() throws Exception {
        HttpResponse<String> response = rawGet("/content/type/atex.WFContentStatus", token);
        assertEquals(200, response.statusCode());

        Map<String, Object> body = parseJson(response.body());
        assertEquals("atex.WFContentStatus", body.get("typeName"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> attributes = (List<Map<String, Object>>) body.get("attributes");
        assertNotNull(findAttribute(attributes, "status"), "status field should exist");
        assertNotNull(findAttribute(attributes, "comment"), "comment field should exist");
    }

    @Test
    void webContentStatus_returnsSchema() throws Exception {
        HttpResponse<String> response = rawGet("/content/type/atex.WebContentStatus", token);
        assertEquals(200, response.statusCode());

        Map<String, Object> body = parseJson(response.body());
        assertEquals("atex.WebContentStatus", body.get("typeName"));
    }

    @Test
    void imageType_returnsSchema() throws Exception {
        HttpResponse<String> response = rawGet("/content/type/atex.onecms.image", token);
        assertEquals(200, response.statusCode());

        Map<String, Object> body = parseJson(response.body());
        assertEquals("atex.onecms.image", body.get("typeName"));
        assertTrue(body.get("beanClass").toString().contains("OneImageBean"));
    }

    @Test
    void insertionInfo_returnsSchema() throws Exception {
        HttpResponse<String> response = rawGet("/content/type/p.InsertionInfo", token);
        assertEquals(200, response.statusCode());

        Map<String, Object> body = parseJson(response.body());
        assertEquals("p.InsertionInfo", body.get("typeName"));
    }

    @Test
    void imageInfoAspect_returnsSchema() throws Exception {
        HttpResponse<String> response = rawGet("/content/type/atex.Image", token);
        assertEquals(200, response.statusCode());

        Map<String, Object> body = parseJson(response.body());
        assertEquals("atex.Image", body.get("typeName"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> attributes = (List<Map<String, Object>>) body.get("attributes");
        assertAttributeType(attributes, "width", "int");
        assertAttributeType(attributes, "height", "int");
        assertAttributeType(attributes, "filePath", "java.lang.String");
    }

    @Test
    void imageEditInfo_returnsSchema() throws Exception {
        HttpResponse<String> response = rawGet("/content/type/atex.ImageEditInfo", token);
        assertEquals(200, response.statusCode());

        Map<String, Object> body = parseJson(response.body());
        assertEquals("atex.ImageEditInfo", body.get("typeName"));
    }

    @Test
    void imageMetadata_returnsSchema() throws Exception {
        HttpResponse<String> response = rawGet("/content/type/atex.ImageMetadata", token);
        assertEquals(200, response.statusCode());

        Map<String, Object> body = parseJson(response.body());
        assertEquals("atex.ImageMetadata", body.get("typeName"));
    }

    @Test
    void contentAccess_returnsSchema() throws Exception {
        HttpResponse<String> response = rawGet("/content/type/p.ContentAccess", token);
        assertEquals(200, response.statusCode());

        Map<String, Object> body = parseJson(response.body());
        assertEquals("p.ContentAccess", body.get("typeName"));
    }

    @Test
    void planning_returnsSchema() throws Exception {
        HttpResponse<String> response = rawGet("/content/type/atex.Planning", token);
        assertEquals(200, response.statusCode());

        Map<String, Object> body = parseJson(response.body());
        assertEquals("atex.Planning", body.get("typeName"));
    }

    @Test
    void metadata_returnsSchema() throws Exception {
        HttpResponse<String> response = rawGet("/content/type/atex.Metadata", token);
        assertEquals(200, response.statusCode());

        Map<String, Object> body = parseJson(response.body());
        assertEquals("atex.Metadata", body.get("typeName"));
    }

    @Test
    void files_returnsSchema() throws Exception {
        HttpResponse<String> response = rawGet("/content/type/atex.Files", token);
        assertEquals(200, response.statusCode());

        Map<String, Object> body = parseJson(response.body());
        assertEquals("atex.Files", body.get("typeName"));
    }

    @Test
    void socialPosting_returnsSchema() throws Exception {
        HttpResponse<String> response = rawGet("/content/type/atex.SocialPosting", token);
        assertEquals(200, response.statusCode());

        Map<String, Object> body = parseJson(response.body());
        assertEquals("atex.SocialPosting", body.get("typeName"));
    }

    @Test
    void video_returnsSchema() throws Exception {
        HttpResponse<String> response = rawGet("/content/type/atex.dam.standard.Video", token);
        assertEquals(200, response.statusCode());

        Map<String, Object> body = parseJson(response.body());
        assertEquals("atex.dam.standard.Video", body.get("typeName"));
    }

    @Test
    void unknownType_returns404() throws Exception {
        HttpResponse<String> response = rawGet("/content/type/nonexistent.type", token);
        assertEquals(404, response.statusCode());
    }

    @Test
    void cacheControlHeader_isSet() throws Exception {
        HttpResponse<String> response = rawGet("/content/type/atex.onecms.article", token);
        assertEquals(200, response.statusCode());

        String cacheControl = response.headers().firstValue("Cache-Control").orElse("");
        assertTrue(cacheControl.contains("max-age=3600"), "Should have 1hr cache");
    }

    @Test
    @SuppressWarnings("unchecked")
    void booleanFields_useCorrectType() throws Exception {
        HttpResponse<String> response = rawGet("/content/type/atex.ImageEditInfo", token);
        assertEquals(200, response.statusCode());

        Map<String, Object> body = parseJson(response.body());
        List<Map<String, Object>> attributes = (List<Map<String, Object>>) body.get("attributes");

        // boolean is* methods should be introspected correctly
        assertAttributeType(attributes, "flipVertical", "boolean");
        assertAttributeType(attributes, "flipHorizontal", "boolean");
    }

    // ---- Helpers ----

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        return new com.google.gson.Gson().fromJson(json, Map.class);
    }

    private Map<String, Object> findAttribute(List<Map<String, Object>> attributes, String name) {
        return attributes.stream()
            .filter(a -> name.equals(a.get("name")))
            .findFirst()
            .orElse(null);
    }

    private void assertAttributeType(List<Map<String, Object>> attributes, String name, String expectedType) {
        Map<String, Object> attr = findAttribute(attributes, name);
        assertNotNull(attr, "Attribute '" + name + "' should exist");
        assertEquals(expectedType, attr.get("typeName"),
            "Attribute '" + name + "' should have type " + expectedType);
    }
}
