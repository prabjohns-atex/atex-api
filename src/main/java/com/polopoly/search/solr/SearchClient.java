package com.polopoly.search.solr;

import org.apache.solr.client.solrj.SolrQuery;

public interface SearchClient {

    SearchResult search(SolrQuery query, int pageSize);
}
