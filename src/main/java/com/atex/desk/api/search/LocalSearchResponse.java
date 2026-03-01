package com.atex.desk.api.search;

import com.atex.onecms.search.SearchResponse;
import com.atex.onecms.ws.search.SearchServiceUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.apache.solr.client.solrj.response.QueryResponse;

/**
 * SearchResponse implementation backed by a SolrJ QueryResponse.
 */
public class LocalSearchResponse implements SearchResponse {

    private final QueryResponse queryResponse;
    private final String core;
    private final int status;
    private final String errorMessage;
    private String cachedJson;

    public LocalSearchResponse(final QueryResponse queryResponse, final String core) {
        this.queryResponse = queryResponse;
        this.core = core;
        this.status = queryResponse != null ? queryResponse.getStatus() : 0;
        this.errorMessage = null;
    }

    public LocalSearchResponse(final int status, final String errorMessage, final String core) {
        this.queryResponse = null;
        this.core = core;
        this.status = status;
        this.errorMessage = errorMessage;
    }

    @Override
    public String getCore() {
        return core;
    }

    @Override
    public QueryResponse response() {
        return queryResponse;
    }

    @Override
    public String json() {
        if (cachedJson == null) {
            if (queryResponse != null) {
                cachedJson = SearchServiceUtil.toJSON(queryResponse.getResponse()).toString();
            } else {
                cachedJson = "{}";
            }
        }
        return cachedJson;
    }

    @Override
    public JsonElement jsonTree() {
        return JsonParser.parseString(json());
    }

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public String getErrorMessage() {
        return errorMessage;
    }
}
