package com.atex.gong.data.publish.page;

import java.util.ArrayList;
import java.util.List;

public class PageResponse {
    private String contentId;
    private List<Container> containers = new ArrayList<>();

    public String getContentId() { return contentId; }
    public void setContentId(String contentId) { this.contentId = contentId; }
    public List<Container> getContainers() { return containers; }
    public void setContainers(List<Container> containers) { this.containers = containers; }

    public Object toJson() { return GsonFactory.create().toJsonTree(this); }
}

