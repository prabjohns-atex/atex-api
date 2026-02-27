package com.atex.desk.api.dto;

public class DeskConfigDto
{
    private String solrCore;
    private String apiUrl;
    private String solrUrl;
    private String damUrl;
    private String slackModuleUrl;
    private String previewUrl;
    private String camelApiUrl;
    private String solrLatestCore;

    public String getSolrCore() { return solrCore; }
    public void setSolrCore(String solrCore) { this.solrCore = solrCore; }

    public String getApiUrl() { return apiUrl; }
    public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }

    public String getSolrUrl() { return solrUrl; }
    public void setSolrUrl(String solrUrl) { this.solrUrl = solrUrl; }

    public String getDamUrl() { return damUrl; }
    public void setDamUrl(String damUrl) { this.damUrl = damUrl; }

    public String getSlackModuleUrl() { return slackModuleUrl; }
    public void setSlackModuleUrl(String slackModuleUrl) { this.slackModuleUrl = slackModuleUrl; }

    public String getPreviewUrl() { return previewUrl; }
    public void setPreviewUrl(String previewUrl) { this.previewUrl = previewUrl; }

    public String getCamelApiUrl() { return camelApiUrl; }
    public void setCamelApiUrl(String camelApiUrl) { this.camelApiUrl = camelApiUrl; }

    public String getSolrLatestCore() { return solrLatestCore; }
    public void setSolrLatestCore(String solrLatestCore) { this.solrLatestCore = solrLatestCore; }
}
