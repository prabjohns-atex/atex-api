package com.atex.onecms.app.dam.solr;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrQuery.ORDER;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.MoreLikeThisParams;

import com.atex.onecms.app.dam.util.DamMapping;
import com.atex.onecms.app.dam.util.Reference;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.IdUtil;
import com.atex.onecms.ws.search.SolrQueryDecorator;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.polopoly.search.solr.SolrServerUrl;

/**
 * Solr search service. Wraps SolrJ Http2SolrClient for executing queries
 * against Solr cores.
 */
public class SolrService extends SolrUtils {

    private static final Logger logger = Logger.getLogger(SolrService.class.getName());

    private static final ConcurrentHashMap<String, SolrClient> SOLRCLIENTS_MAP = new ConcurrentHashMap<>();

    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    private final String solrServerUrl;
    private final String core;
    private final String baseUrl;

    public SolrService(SolrServerUrl solrServerUrl, String core) {
        Objects.requireNonNull(solrServerUrl, "solrServerUrl cannot be null");
        Objects.requireNonNull(core, "core cannot be null");

        this.solrServerUrl = solrServerUrl.getUrl();
        this.core = core;
        String separator = this.solrServerUrl.endsWith("/") ? "" : "/";
        this.baseUrl = this.solrServerUrl + separator + core;
    }

    public String autocomplete(String str, String field) throws Exception {
        String term = ".*" + str + ".*";
        SolrQuery query = new SolrQuery(term)
                .setRequestHandler("/terms")
                .setTerms(true)
                .addTermsField(field)
                .setTermsRegex(term)
                .setTermsRegexFlag("case_insensitive")
                .setTermsMinCount(1);

        return queryAsJson(query);
    }

    public String terms(String str, String field, int limit) throws Exception {
        SolrQuery query = new SolrQuery()
                .setRequestHandler("/terms")
                .setTerms(true)
                .addTermsField(field)
                .setTermsRegex(str)
                .setTermsRegexFlag("case_insensitive")
                .setTermsLimit(limit)
                .setTermsMinCount(1);

        return queryAsJson(query);
    }

    public int recordCount(SolrQuery solrQuery) throws Exception {
        SolrQuery query = solrQuery.getCopy();
        query.setRows(0);

        QueryResponse response = executeQuery(query);
        return (int) response.getResults().getNumFound();
    }

    public int getLatestEventId() throws Exception {
        SolrQuery query = new SolrQuery("*:*")
                .setRows(1)
                .setFields("eventId")
                .setSort("eventId", ORDER.desc);

        QueryResponse response = executeQuery(query);
        SolrDocumentList docs = response.getResults();
        if (docs != null && !docs.isEmpty()) {
            Object eventId = docs.get(0).get("eventId");
            if (eventId instanceof Number) {
                return ((Number) eventId).intValue() + 1;
            }
        }
        return 0;
    }

    public String mlt(String q, String interestingTerms, String boost, String matchInclude,
                      String fl, String minwl, String mindf, String mintf) throws Exception {
        SolrQuery query = new SolrQuery(q)
                .setRequestHandler("/mlt")
                .setMoreLikeThis(true)
                .setMoreLikeThisBoost(Boolean.parseBoolean(boost))
                .addMoreLikeThisField(fl)
                .setMoreLikeThisMinWordLen(Integer.parseInt(minwl))
                .setMoreLikeThisMinDocFreq(Integer.parseInt(mindf))
                .setMoreLikeThisMinTermFreq(Integer.parseInt(mintf));
        query.set(MoreLikeThisParams.INTERESTING_TERMS, interestingTerms);
        query.set(MoreLikeThisParams.MATCH_INCLUDE, matchInclude);

        return queryAsJson(query);
    }

    public Integer size(String q, List<ContentId> workingSites) throws Exception {
        SolrQuery query = new SolrQuery();
        query.set("q", q);

        if (workingSites != null) {
            query = new SolrQueryDecorator(query).decorateWithWorkingSites(workingSites).getSolrQuery();
        }

        query.setRows(0);
        QueryResponse response = executeQuery(query);
        return (int) response.getResults().getNumFound();
    }

    public Reference query(String q, String fq, int rows, List<ContentId> workingSites) throws Exception {
        return querySorted(q, fq, rows, workingSites, null);
    }

    public Reference querySorted(String q, String fq, int rows, List<ContentId> workingSites,
                                 SolrQuery.SortClause sortClause) throws Exception {
        SolrQuery query = new SolrQuery();
        query.set("q", q);

        if (fq != null && !fq.isEmpty()) {
            query.set("fq", fq);
        }
        if (sortClause != null) {
            query.setSort(sortClause);
        }

        if (workingSites != null) {
            query = new SolrQueryDecorator(query).decorateWithWorkingSites(workingSites).getSolrQuery();
        }

        if (rows > 0) {
            query.setRows(rows);
        }

        QueryResponse response = executeQuery(query);
        return getReference(response.getResults());
    }

    public Reference resources(String id, List<ContentId> workingSites) throws Exception {
        String q = "resources_atex_desk_sms: \"" + id + "\"";

        SolrQuery query = new SolrQuery();
        query.set("q", q);
        if (workingSites != null) {
            query = new SolrQueryDecorator(query).decorateWithWorkingSites(workingSites).getSolrQuery();
        }
        QueryResponse response = executeQuery(query);
        return getReference(response.getResults());
    }

    public Reference related(String id, List<ContentId> workingSites) throws Exception {
        String q = "atex_desk_legacyid_t: \"" + id + "\"";

        SolrQuery query = new SolrQuery();
        query.set("q", q);
        if (workingSites != null) {
            query = new SolrQueryDecorator(query).decorateWithWorkingSites(workingSites).getSolrQuery();
        }
        QueryResponse response = executeQuery(query);
        return getReference(response.getResults());
    }

    public Reference jobs(String id, List<ContentId> workingSites) throws Exception {
        String q = "tasks_atex_desk_sms: \"" + id + "\"";

        SolrQuery query = new SolrQuery();
        query.set("q", q);
        if (workingSites != null) {
            query = new SolrQueryDecorator(query).decorateWithWorkingSites(workingSites).getSolrQuery();
        }
        QueryResponse response = executeQuery(query);
        return getReference(response.getResults());
    }

    public java.util.Map<String, String> getHighlightMap(ContentVersionId vci, String q) throws Exception {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        String contentId = IdUtil.toIdString(vci.getContentId());
        String vers = IdUtil.toVersionedIdString(vci);

        SolrQuery query = new SolrQuery();
        if (q.trim().length() > 0) {
            query.setQuery(q);
            query.setFilterQueries("+version:(" + ClientUtils.escapeQueryChars(vers) + ")");
            query.setHighlight(true)
                 .setHighlightSnippets(1)
                 .setTermsRegexFlag("case_insensitive")
                 .setHighlightFragsize(50000);
            query.setParam("hl.fl", "*");
            query.setHighlightSimplePre("<span class='preview-text--highlighted'>");
            query.setHighlightSimplePost("</span>");
            if (!q.contains("atex_desk_text:")) {
                query.setParam("hl.usePhraseHighlighter", true);
                query.setParam("hl.requireFieldMatch", true);
            }
        } else {
            query.setQuery("+version:(" + vers + ")");
        }

        QueryResponse response = executeQuery(query);
        if (response.getResults() != null && !response.getResults().isEmpty()
                && response.getHighlighting() != null) {
            String highlightKey = findHighlightKey(contentId, response.getHighlighting());
            if (highlightKey != null) {
                java.util.Map<String, java.util.List<String>> solrmap = response.getHighlighting().get(highlightKey);
                if (solrmap != null) {
                    for (var entry : solrmap.entrySet()) {
                        String fieldName = DamMapping.getFieldNameBySolrFieldName(entry.getKey());
                        if (fieldName != null && !entry.getValue().isEmpty()) {
                            map.put(fieldName, entry.getValue().get(0));
                        }
                    }
                }
            }
        }
        return map;
    }

    private String findHighlightKey(String contentId,
                                    java.util.Map<String, java.util.Map<String, java.util.List<String>>> highlighting) {
        for (String key : highlighting.keySet()) {
            String id = stripShardKey(key);
            if (id.equalsIgnoreCase(contentId)) {
                return key;
            }
        }
        return null;
    }

    /**
     * Strip shard routing key prefix (e.g. "shard!id" -> "id").
     */
    private static String stripShardKey(String id) {
        if (id != null) {
            int idx = id.indexOf('!');
            if (idx >= 0) {
                return id.substring(idx + 1);
            }
        }
        return id;
    }

    private Reference getReference(SolrDocumentList documentList) {
        Reference reference = new Reference();
        if (documentList != null && !documentList.isEmpty()) {
            ArrayList<String> references = new ArrayList<>();
            for (SolrDocument document : documentList) {
                references.add(stripShardKey((String) document.get("id")));
            }
            reference.setReferences(references);
        }
        return reference;
    }

    /**
     * Execute a query and return the response as a JSON string.
     * Converts the Solr response to a Gson JsonObject for serialization.
     */
    private String queryAsJson(SolrQuery query) throws Exception {
        QueryResponse response = executeQuery(query);
        return serializeResponse(response);
    }

    /**
     * Serialize a QueryResponse to JSON using Gson.
     */
    private String serializeResponse(QueryResponse response) {
        JsonObject root = new JsonObject();

        // Response header
        if (response.getHeader() != null) {
            JsonObject header = new JsonObject();
            header.addProperty("status", response.getStatus());
            root.add("responseHeader", header);
        }

        // Results
        SolrDocumentList results = response.getResults();
        if (results != null) {
            JsonObject responseObj = new JsonObject();
            responseObj.addProperty("numFound", results.getNumFound());
            responseObj.addProperty("start", results.getStart());

            JsonArray docs = new JsonArray();
            for (SolrDocument doc : results) {
                JsonObject docObj = new JsonObject();
                for (String field : doc.getFieldNames()) {
                    Object val = doc.getFieldValue(field);
                    if (val instanceof Number) {
                        docObj.addProperty(field, (Number) val);
                    } else if (val instanceof Boolean) {
                        docObj.addProperty(field, (Boolean) val);
                    } else if (val instanceof java.util.Collection) {
                        JsonArray arr = new JsonArray();
                        for (Object item : (java.util.Collection<?>) val) {
                            arr.add(Objects.toString(item));
                        }
                        docObj.add(field, arr);
                    } else {
                        docObj.addProperty(field, Objects.toString(val));
                    }
                }
                docs.add(docObj);
            }
            responseObj.add("docs", docs);
            root.add("response", responseObj);
        }

        // Terms
        if (response.getTermsResponse() != null) {
            JsonObject termsObj = new JsonObject();
            for (var termField : response.getTermsResponse().getTermMap().entrySet()) {
                JsonObject fieldTerms = new JsonObject();
                for (var term : termField.getValue()) {
                    fieldTerms.addProperty(term.getTerm(), term.getFrequency());
                }
                termsObj.add(termField.getKey(), fieldTerms);
            }
            root.add("terms", termsObj);
        }

        return gson.toJson(root);
    }

    private QueryResponse executeQuery(SolrQuery query) throws Exception {
        try {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "Query: {0}", query.toQueryString());
            }
            QueryResponse response = getSolrClient().query(core, query);
            int status = response.getStatus();
            if (status != 0) {
                throw new Exception("Solr query returned status " + status);
            }
            return response;
        } catch (SolrServerException | IOException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
            throw new Exception("Solr query failed: " + e.getMessage(), e);
        }
    }

    /**
     * Index a JSON document into a Solr collection.
     */
    public void index(String collection, com.google.gson.JsonObject solrDoc) throws Exception {
        try {
            SolrInputDocument doc = jsonToSolrDoc(solrDoc);
            getSolrClient().add(collection, doc);
            getSolrClient().commit(collection);
        } catch (SolrServerException | IOException e) {
            logger.log(Level.SEVERE, "Failed to index document: " + e.getMessage(), e);
            throw new Exception("Solr index failed: " + e.getMessage(), e);
        }
    }

    /**
     * Delete a document from a Solr collection by ID.
     */
    public void delete(String collection, String id) throws Exception {
        try {
            getSolrClient().deleteById(collection, id);
            getSolrClient().commit(collection);
        } catch (SolrServerException | IOException e) {
            logger.log(Level.SEVERE, "Failed to delete document: " + e.getMessage(), e);
            throw new Exception("Solr delete failed: " + e.getMessage(), e);
        }
    }

    private SolrInputDocument jsonToSolrDoc(com.google.gson.JsonObject json) {
        SolrInputDocument doc = new SolrInputDocument();
        for (var entry : json.entrySet()) {
            String field = entry.getKey();
            com.google.gson.JsonElement value = entry.getValue();
            if (value.isJsonNull()) continue;
            if (value.isJsonPrimitive()) {
                var prim = value.getAsJsonPrimitive();
                if (prim.isBoolean()) {
                    doc.addField(field, prim.getAsBoolean());
                } else if (prim.isNumber()) {
                    doc.addField(field, prim.getAsNumber());
                } else {
                    doc.addField(field, prim.getAsString());
                }
            } else if (value.isJsonArray()) {
                for (com.google.gson.JsonElement el : value.getAsJsonArray()) {
                    if (el.isJsonPrimitive()) {
                        doc.addField(field, el.getAsString());
                    }
                }
            } else {
                doc.addField(field, gson.toJson(value));
            }
        }
        return doc;
    }

    protected SolrClient getSolrClient() {
        return SOLRCLIENTS_MAP.computeIfAbsent(baseUrl, u ->
            new Http2SolrClient.Builder(solrServerUrl).build()
        );
    }
}
