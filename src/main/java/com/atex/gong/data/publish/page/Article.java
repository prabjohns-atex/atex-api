package com.atex.gong.data.publish.page;

import java.util.Map;

public class Article {
    private String contentId;
    private Map<String, String> teaserMap;

    public String getContentId() { return contentId; }
    public void setContentId(String contentId) { this.contentId = contentId; }
    public Map<String, String> getTeaserMap() { return teaserMap; }
    public void setTeaserMap(Map<String, String> teaserMap) { this.teaserMap = teaserMap; }
}

