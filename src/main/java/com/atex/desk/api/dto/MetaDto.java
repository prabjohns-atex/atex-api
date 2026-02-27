package com.atex.desk.api.dto;

/**
 * Matches the OneCMS ContentResult.Meta JSON structure.
 * Times are epoch milliseconds as strings.
 */
public class MetaDto
{
    private String modificationTime;
    private String originalCreationTime;

    public String getModificationTime() { return modificationTime; }
    public void setModificationTime(String modificationTime) { this.modificationTime = modificationTime; }

    public String getOriginalCreationTime() { return originalCreationTime; }
    public void setOriginalCreationTime(String originalCreationTime) { this.originalCreationTime = originalCreationTime; }
}
