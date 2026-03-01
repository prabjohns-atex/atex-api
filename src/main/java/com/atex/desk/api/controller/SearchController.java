package com.atex.desk.api.controller;

import com.atex.desk.api.search.LocalSearchClient;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.Subject;
import com.atex.onecms.search.SearchOptions;
import com.atex.onecms.search.SearchResponse;
import com.atex.onecms.ws.search.SearchServiceUtil;
import com.atex.onecms.ws.search.SolrFormat;
import com.atex.onecms.app.dam.ws.DamUserContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.util.NamedList;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Search endpoint for direct Solr proxy access with format negotiation (JSON/XML),
 * permission filtering, and working-sites decoration.
 * Ported from the original SearchResource.
 */
@RestController
@RequestMapping("/search")
@Tag(name = "Search")
public class SearchController {

    private static final Logger LOG = Logger.getLogger(SearchController.class.getName());

    private final LocalSearchClient searchClient;
    private final ContentManager contentManager;

    public SearchController(LocalSearchClient searchClient,
                            @Nullable ContentManager contentManager) {
        this.searchClient = searchClient;
        this.contentManager = contentManager;
    }

    @GetMapping("/{core}/select")
    @Operation(summary = "Search via GET", description = "Execute a Solr query via GET with query string parameters")
    @ApiResponse(responseCode = "200", description = "Search results in requested format (JSON or XML)")
    public ResponseEntity<?> searchGet(
            @Parameter(description = "Solr core name") @PathVariable("core") String core,
            HttpServletRequest request) {
        return process(core, request.getQueryString(), request);
    }

    @PostMapping(value = "/{core}/select", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Operation(summary = "Search via POST (form)", description = "Execute a Solr query via POST with form-encoded parameters")
    @ApiResponse(responseCode = "200", description = "Search results in requested format (JSON or XML)")
    public ResponseEntity<?> searchPostForm(
            @Parameter(description = "Solr core name") @PathVariable("core") String core,
            HttpServletRequest request) {
        // For form POST, reconstruct query string from parameters
        StringBuilder qs = new StringBuilder();
        var paramMap = request.getParameterMap();
        for (var entry : paramMap.entrySet()) {
            for (String value : entry.getValue()) {
                if (!qs.isEmpty()) qs.append('&');
                qs.append(java.net.URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
                qs.append('=');
                qs.append(java.net.URLEncoder.encode(value, StandardCharsets.UTF_8));
            }
        }
        return process(core, qs.toString(), request);
    }

    @PostMapping(value = "/{core}/select", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Search via POST (JSON)", description = "Execute a Solr query via POST with JSON body")
    @ApiResponse(responseCode = "200", description = "Search results in requested format (JSON or XML)")
    public ResponseEntity<?> searchPostJson(
            @Parameter(description = "Solr core name") @PathVariable("core") String core,
            @RequestBody String body,
            HttpServletRequest request) {
        // Parse JSON body into query string
        String queryString = jsonBodyToQueryString(body);
        return process(core, queryString, request);
    }

    private ResponseEntity<?> process(String core, String queryString, HttpServletRequest request) {
        try {
            // Parse query
            SolrQuery solrQuery = SearchServiceUtil.parseQueryString(queryString);

            // Determine response format
            String wt = solrQuery.get("wt");
            SolrFormat format = SearchServiceUtil.deduceResponseType(wt);

            // Extract and remove non-Solr params
            String variant = solrQuery.get("variant");
            String permissionParam = solrQuery.get("permission");
            solrQuery.remove("variant");
            solrQuery.remove("permission");

            // Get auth context (allow anonymous — filter sets null user if no token)
            Subject subject = null;
            try {
                DamUserContext ctx = DamUserContext.from(request);
                if (ctx.isLoggedIn()) {
                    subject = ctx.getSubject();
                }
            } catch (Exception e) {
                // Anonymous access allowed
            }

            // Build search options
            SearchOptions.ACCESS_PERMISSION permission = SearchOptions.ACCESS_PERMISSION.from(permissionParam);
            SearchOptions options = SearchOptions.none()
                .postMethod("POST".equalsIgnoreCase(request.getMethod()))
                .permission(permission);

            // Execute search
            SearchResponse searchResponse = searchClient.query(core, solrQuery, subject, options);

            if (searchResponse.getErrorMessage() != null) {
                return SearchServiceUtil.solrError(
                    searchResponse.getStatus(),
                    searchResponse.getErrorMessage(),
                    format);
            }

            QueryResponse queryResponse = searchResponse.response();
            if (queryResponse == null) {
                return SearchServiceUtil.solrError(500, "No response from Solr", format);
            }

            NamedList<Object> response = queryResponse.getResponse();

            // Inline content data if variant is set
            if (variant != null && !variant.isEmpty() && contentManager != null && response != null) {
                inlineContentData(response, variant);
            }

            return SearchServiceUtil.formatResponse(200, format, response);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Search processing failed", e);
            return SearchServiceUtil.solrError(500, "Internal error: " + e.getMessage(), SolrFormat.json);
        }
    }

    private void inlineContentData(NamedList<Object> response, String variant) {
        Object responseObj = response.get("response");
        if (!(responseObj instanceof SolrDocumentList docList)) return;

        for (SolrDocument doc : docList) {
            Object idObj = doc.getFieldValue("id");
            if (idObj == null) continue;
            String contentId = idObj.toString();

            try {
                var cid = com.atex.onecms.content.IdUtil.fromString(contentId);
                if (cid == null) continue;
                var vid = contentManager.resolve(cid, Subject.NOBODY_CALLER);
                if (vid == null) continue;
                var result = contentManager.get(vid, Object.class, Subject.NOBODY_CALLER);
                if (result != null && result.getContent() != null) {
                    // Serialize content as JSON and add as _data field
                    com.google.gson.Gson gson = new com.google.gson.Gson();
                    String json = gson.toJson(result.getContent().getContentData());
                    doc.setField("_data", json);
                }
            } catch (Exception e) {
                LOG.log(Level.FINE, "Failed to inline content for " + contentId, e);
            }
        }
    }

    private String jsonBodyToQueryString(String body) {
        if (body == null || body.isBlank()) return "";

        try {
            JsonElement parsed = JsonParser.parseString(body);
            if (!parsed.isJsonObject()) return "";
            JsonObject obj = parsed.getAsJsonObject();

            StringBuilder qs = new StringBuilder();
            for (var entry : obj.entrySet()) {
                JsonElement value = entry.getValue();
                if (value.isJsonArray()) {
                    for (JsonElement item : value.getAsJsonArray()) {
                        if (!qs.isEmpty()) qs.append('&');
                        qs.append(java.net.URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
                        qs.append('=');
                        qs.append(java.net.URLEncoder.encode(item.getAsString(), StandardCharsets.UTF_8));
                    }
                } else {
                    if (!qs.isEmpty()) qs.append('&');
                    qs.append(java.net.URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
                    qs.append('=');
                    qs.append(java.net.URLEncoder.encode(value.getAsString(), StandardCharsets.UTF_8));
                }
            }
            return qs.toString();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to parse JSON body as query params", e);
            return "";
        }
    }
}
