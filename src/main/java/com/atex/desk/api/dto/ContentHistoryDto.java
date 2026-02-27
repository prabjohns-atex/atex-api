package com.atex.desk.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Matches the OneCMS ContentHistory JSON structure.
 */
@Schema(description = "Content version history")
public class ContentHistoryDto
{
    @Schema(description = "The list of content versions")
    private List<ContentVersionInfoDto> versions;

    public List<ContentVersionInfoDto> getVersions() { return versions; }
    public void setVersions(List<ContentVersionInfoDto> versions) { this.versions = versions; }
}
