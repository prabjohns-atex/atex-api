package com.atex.onecms.app.dam;
public class DeskConfig {
    private String solrCore;
    private String apiUrl;
    private String solrUrl;
    private String damUrl;
    private String slackModuleUrl;
    private String previewUrl;
    private String camelApiUrl;
    private String solrLatestCore;
    public String getSolrCore() { return solrCore; }
    public void setSolrCore(String v) { this.solrCore = v; }
    public String getApiUrl() { return apiUrl; }
    public void setApiUrl(String v) { this.apiUrl = v; }
    public String getSolrUrl() { return solrUrl; }
    public void setSolrUrl(String v) { this.solrUrl = v; }
    public String getDamUrl() { return damUrl; }
    public void setDamUrl(String v) { this.damUrl = v; }
    public String getSlackModuleUrl() { return slackModuleUrl; }
    public void setSlackModuleUrl(String v) { this.slackModuleUrl = v; }
    public String getPreviewUrl() { return previewUrl; }
    public void setPreviewUrl(String v) { this.previewUrl = v; }
    public String getCamelApiUrl() { return camelApiUrl; }
    public void setCamelApiUrl(String v) { this.camelApiUrl = v; }
    public String getSolrLatestCore() { return solrLatestCore; }
    public void setSolrLatestCore(String v) { this.solrLatestCore = v; }
    public void setupDamUtils() {
        com.atex.onecms.app.dam.util.DamUtils.setDamUrl(damUrl);
        com.atex.onecms.app.dam.util.DamUtils.setApiUrl(apiUrl);
        com.atex.onecms.app.dam.util.DamUtils.setPreviewUrl(previewUrl);
        com.atex.onecms.app.dam.util.DamUtils.setSlackModuleUrl(slackModuleUrl);
    }
}
