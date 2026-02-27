package com.polopoly.search.solr;

/**
 * Configuration for a Solr connection.
 */
public class SolrConfiguration extends SolrServerUrl {

    private String core;
    private String auth;

    public SolrConfiguration(String url, String core) {
        super(url);
        this.core = core;
    }

    public String getCore() { return core; }
    public void setCore(String core) { this.core = core; }
    public String getAuth() { return auth; }
    public void setAuth(String auth) { this.auth = auth; }

    public SolrConfiguration withCore(String core) {
        return new SolrConfiguration(getUrl(), core);
    }

    public boolean hasAuth() {
        return auth != null && !auth.isEmpty();
    }
}
