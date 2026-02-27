package com.atex.onecms.app.dam.solr;

import com.polopoly.search.solr.SolrServerUrl;

/**
 * Static utility providing Solr server URL and core configuration.
 */
public class SolrUtils {

    private static SolrServerUrl solrServerUrl;
    private static String core;

    public static SolrServerUrl getSolrServerUrl() {
        return solrServerUrl;
    }

    public static void setSolrServerUrl(SolrServerUrl url) {
        solrServerUrl = url;
    }

    public static String getCore() {
        return core;
    }

    public static void setCore(String coreName) {
        core = coreName;
    }
}
