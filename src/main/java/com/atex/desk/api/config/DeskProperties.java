package com.atex.desk.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "desk")
public class DeskProperties
{
    private String solrUrl = "http://localhost:8983/solr";
    private String solrCore = "onecms";
    private String solrLatestCore = "onecms";
    private String apiUrl = "http://localhost:8081";
    private String damUrl = "http://localhost:8081/dam";
    private String previewUrl = "";
    private String camelApiUrl = "";
    private String slackModuleUrl = "";

    public String getSolrUrl()
    {
        return solrUrl;
    }

    public void setSolrUrl(String solrUrl)
    {
        this.solrUrl = solrUrl;
    }

    public String getSolrCore()
    {
        return solrCore;
    }

    public void setSolrCore(String solrCore)
    {
        this.solrCore = solrCore;
    }

    public String getSolrLatestCore()
    {
        return solrLatestCore;
    }

    public void setSolrLatestCore(String solrLatestCore)
    {
        this.solrLatestCore = solrLatestCore;
    }

    public String getApiUrl()
    {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl)
    {
        this.apiUrl = apiUrl;
    }

    public String getDamUrl()
    {
        return damUrl;
    }

    public void setDamUrl(String damUrl)
    {
        this.damUrl = damUrl;
    }

    public String getPreviewUrl()
    {
        return previewUrl;
    }

    public void setPreviewUrl(String previewUrl)
    {
        this.previewUrl = previewUrl;
    }

    public String getCamelApiUrl()
    {
        return camelApiUrl;
    }

    public void setCamelApiUrl(String camelApiUrl)
    {
        this.camelApiUrl = camelApiUrl;
    }

    public String getSlackModuleUrl()
    {
        return slackModuleUrl;
    }

    public void setSlackModuleUrl(String slackModuleUrl)
    {
        this.slackModuleUrl = slackModuleUrl;
    }
}
