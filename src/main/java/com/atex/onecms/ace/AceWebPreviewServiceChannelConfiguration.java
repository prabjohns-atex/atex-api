package com.atex.onecms.ace;

/**
 * Configuration for ACE web preview adapter.
 * Fields populated from the remotes configuration "previewConfig" object.
 */
public class AceWebPreviewServiceChannelConfiguration {

    private String acePreviewBaseUrl;
    private String acePreviewVariant;
    private String[] acePreviewStatusList;

    public String getAcePreviewBaseUrl() { return acePreviewBaseUrl; }
    public void setAcePreviewBaseUrl(String v) { this.acePreviewBaseUrl = v; }

    public String getAcePreviewVariant() { return acePreviewVariant; }
    public void setAcePreviewVariant(String v) { this.acePreviewVariant = v; }

    public String[] getAcePreviewStatusList() { return acePreviewStatusList; }
    public void setAcePreviewStatusList(String[] v) { this.acePreviewStatusList = v; }
}
