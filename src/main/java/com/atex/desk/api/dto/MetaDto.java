package com.atex.desk.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Matches the OneCMS ContentResult.Meta JSON structure.
 * Times are epoch milliseconds as strings.
 */
@Schema(description = "Metadata information about a content")
public class MetaDto
{
    @Schema(description = "The modification time (epoch time in milliseconds)")
    private String modificationTime;

    @Schema(description = "The content creation time (epoch time in milliseconds)")
    private String originalCreationTime;

    @Schema(description = "Content aliases (namespace → value)")
    private Map<String, String> aliases;

    public String getModificationTime() { return modificationTime; }
    public void setModificationTime(String modificationTime) { this.modificationTime = modificationTime; }

    public String getOriginalCreationTime() { return originalCreationTime; }
    public void setOriginalCreationTime(String originalCreationTime) { this.originalCreationTime = originalCreationTime; }

    public Map<String, String> getAliases() { return aliases; }
    public void setAliases(Map<String, String> aliases) { this.aliases = aliases; }
}
