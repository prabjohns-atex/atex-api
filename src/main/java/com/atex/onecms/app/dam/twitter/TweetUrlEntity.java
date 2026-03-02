package com.atex.onecms.app.dam.twitter;
public class TweetUrlEntity {
    private String displayURL = null;
    private String expandedURL = null;
    public TweetUrlEntity(String displayURL, String expandedURL) {
        this.displayURL = displayURL;
        this.expandedURL = expandedURL;
    }
    public TweetUrlEntity() {}
    public String getDisplayURL() { return displayURL; }
    public void setDisplayURL(String displayURL) { this.displayURL = displayURL; }
    public String getExpandedURL() { return expandedURL; }
    public void setExpandedURL(String expandedURL) { this.expandedURL = expandedURL; }
}
