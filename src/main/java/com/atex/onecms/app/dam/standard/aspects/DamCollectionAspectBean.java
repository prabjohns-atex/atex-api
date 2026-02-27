package com.atex.onecms.app.dam.standard.aspects;

import java.util.ArrayList;
import java.util.List;

public class DamCollectionAspectBean extends OneContentBean {
    private List<String> contentIds;
    private String headline;
    private String description;

    public List<String> getContentIds() { return contentIds; }
    public void setContentIds(List<String> v) { this.contentIds = v; }

    public List<String> getContentIdList() {
        if (contentIds == null) {
            contentIds = new ArrayList<>();
        }
        return contentIds;
    }

    public String getHeadline() { return headline; }
    public void setHeadline(String v) { this.headline = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
}
