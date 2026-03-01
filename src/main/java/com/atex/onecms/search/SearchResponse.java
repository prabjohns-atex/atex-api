package com.atex.onecms.search;

import com.google.gson.JsonElement;
import org.apache.solr.client.solrj.response.QueryResponse;

public interface SearchResponse {

    String getCore();

    QueryResponse response();

    String json();

    JsonElement jsonTree();

    int getStatus();

    String getErrorMessage();
}
