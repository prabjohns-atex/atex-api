package com.atex.desk.api.dto;

import java.util.List;

/**
 * Matches the OneCMS ContentHistory JSON structure.
 */
public class ContentHistoryDto
{
    private List<ContentVersionInfoDto> versions;

    public List<ContentVersionInfoDto> getVersions() { return versions; }
    public void setVersions(List<ContentVersionInfoDto> versions) { this.versions = versions; }
}
