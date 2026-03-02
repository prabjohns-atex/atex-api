package com.atex.onecms.app.dam.types;

import com.atex.onecms.content.ContentId;

public class SocialPostingPlatform {

    private String postText;
    private ContentId postMedia;
    private String title;
    private String description;

    public String getPostText() { return postText; }
    public void setPostText(final String postText) { this.postText = postText; }
    public ContentId getPostMedia() { return postMedia; }
    public void setPostMedia(final ContentId postMedia) { this.postMedia = postMedia; }
    public String getTitle() { return title; }
    public void setTitle(final String postTitle) { this.title = postTitle; }
    public String getDescription() { return description; }
    public void setDescription(final String description) { this.description = description; }
}

