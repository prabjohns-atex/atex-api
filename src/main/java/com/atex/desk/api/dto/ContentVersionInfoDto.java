package com.atex.desk.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Matches the OneCMS ContentVersionInfo JSON structure.
 */
@Schema(description = "Content version info")
public class ContentVersionInfoDto
{
    @Schema(description = "The content version id")
    private String version;

    @Schema(description = "The creation time (in milliseconds)")
    private long creationTime;

    @Schema(description = "The creator's userId")
    private String creatorId;

    @Schema(description = "The list of views")
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
