package com.atex.onecms.app.dam.twitter;

public class TweetMediaEntity {

    private long id = -1;
    private String displayURL = null;
    private String expandedURL = null;
    private String mediaURL = null;
    private String type = null;

    public TweetMediaEntity(long id, String displayURL, String expandedURL, String mediaURL, String type) {
        this.id = id;
        this.displayURL = displayURL;
        this.expandedURL = expandedURL;
        this.mediaURL = mediaURL;
        this.type = type;
    }

    public TweetMediaEntity() {}

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getDisplayURL() { return displayURL; }
    public void setDisplayURL(String displayURL) { this.displayURL = displayURL; }
    public String getExpandedURL() { return expandedURL; }
    public void setExpandedURL(String expandedURL) { this.expandedURL = expandedURL; }
    public String getMediaURL() { return mediaURL; }
    public void setMediaURL(String mediaURL) { this.mediaURL = mediaURL; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}

