package com.polopoly.search.solr;

public class SolrIndexName {

    private final String name;

    public SolrIndexName(final String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
