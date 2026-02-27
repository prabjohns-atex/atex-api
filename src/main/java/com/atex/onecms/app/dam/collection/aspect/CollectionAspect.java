package com.atex.onecms.app.dam.collection.aspect;

import java.util.List;

public class CollectionAspect {
    public static final String ASPECT_NAME = "collectionAspect";

    private List<String> contentIds;
    private String contentId;
    private String name;
    public List<String> getContentIds() { return contentIds; }
    public void setContentIds(List<String> v) { this.contentIds = v; }
    public String getContentId() { return contentId; }
    public void setContentId(String v) { this.contentId = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
}
