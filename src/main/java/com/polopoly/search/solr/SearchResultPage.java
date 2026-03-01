package com.polopoly.search.solr;

import com.atex.onecms.content.ContentId;
import org.apache.solr.client.solrj.response.QueryResponse;

import java.util.List;

public interface SearchResultPage {

    List<ContentId> getHits();

    List<QueryResponse> getQueryResponses();

    boolean isEmpty();

    boolean isIncomplete();
}
