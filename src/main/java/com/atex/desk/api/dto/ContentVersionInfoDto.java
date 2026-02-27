package com.atex.desk.api.dto;

import java.util.List;

/**
 * Matches the OneCMS ContentVersionInfo JSON structure.
 */
public class ContentVersionInfoDto
{
    private String version;
    private long creationTime;
    private String creatorId;
    private List<String> views;

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public long getCreationTime() { return creationTime; }
    public void setCreationTime(long creationTime) { this.creationTime = creationTime; }

    public String getCreatorId() { return creatorId; }
    public void setCreatorId(String creatorId) { this.creatorId = creatorId; }

    public List<String> getViews() { return views; }
    public void setViews(List<String> views) { this.views = views; }
}
