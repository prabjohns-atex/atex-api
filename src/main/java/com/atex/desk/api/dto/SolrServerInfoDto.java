package com.atex.desk.api.dto;

public class SolrServerInfoDto
{
    private String solrUrl;
    private String solrCore;
    private String solrLatestCore;

    public String getSolrUrl() { return solrUrl; }
    public void setSolrUrl(String solrUrl) { this.solrUrl = solrUrl; }

    public String getSolrCore() { return solrCore; }
    public void setSolrCore(String solrCore) { this.solrCore = solrCore; }

    public String getSolrLatestCore() { return solrLatestCore; }
    public void setSolrLatestCore(String solrLatestCore) { this.solrLatestCore = solrLatestCore; }
}
