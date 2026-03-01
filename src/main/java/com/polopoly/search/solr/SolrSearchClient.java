package com.polopoly.search.solr;

import org.apache.solr.client.solrj.SolrQuery;

public class SolrSearchClient implements SearchClient {

    private final SolrIndexName indexName;

    public SolrSearchClient(final SolrIndexName indexName) {
        this.indexName = indexName;
    }

    public SolrIndexName getIndexName() {
        return indexName;
    }

    @Override
    public SearchResult search(final SolrQuery query, final int pageSize) {
        throw new UnsupportedOperationException("Use LocalSearchClient");
    }
}
