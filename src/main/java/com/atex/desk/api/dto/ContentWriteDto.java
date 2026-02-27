package com.atex.desk.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Matches the OneCMS Content JSON structure for create/update requests.
 */
@Schema(description = "The content model used for create and update operations")
public class ContentWriteDto
{
    @Schema(description = "The non versioned contentId")
    private String id;

    @Schema(description = "The versioned contentId")
    private String version;

    @Schema(description = "Aspects, 'contentData' contains the main aspect")
    private Map<String, AspectDto> aspects;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public Map<String, AspectDto> getAspects() { return aspects; }
    public void setAspects(Map<String, AspectDto> aspects) { this.aspects = aspects; }
}
