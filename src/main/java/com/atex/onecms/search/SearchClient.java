package com.atex.onecms.search;

import com.atex.onecms.content.Subject;
import org.apache.solr.client.solrj.SolrQuery;

public interface SearchClient {

    String CORE_ONECMS = "onecms";
    String CORE_LATEST = "latest";
    String CORE_DEFAULT = CORE_ONECMS;

    SearchResponse query(String core, SolrQuery query, Subject subject, SearchOptions options);

    default SearchResponse query(SolrQuery query, Subject subject) {
        return query(CORE_DEFAULT, query, subject, SearchOptions.none());
    }

    default SearchResponse query(String core, SolrQuery query, Subject subject) {
        return query(core, query, subject, SearchOptions.none());
    }

    default SearchResponse query(SolrQuery query, Subject subject, SearchOptions options) {
        return query(CORE_DEFAULT, query, subject, options);
    }
}
