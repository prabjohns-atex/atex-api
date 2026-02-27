package com.atex.desk.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Matches the OneCMS ContentResult JSON structure.
 * Extends the Content structure with version metadata.
 */
@Schema(description = "The content model when fetching a content")
public class ContentResultDto
{
    @Schema(description = "The non versioned contentId", example = "onecms:abc123")
    private String id;

    @Schema(description = "The versioned contentId", example = "onecms:abc123:v1")
    private String version;

    @Schema(description = "Aspects, 'contentData' contains the main aspect")
    private Map<String, AspectDto> aspects;

    @Schema(description = "Metadata about this version")
    private MetaDto meta;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public Map<String, AspectDto> getAspects() { return aspects; }
    public void setAspects(Map<String, AspectDto> aspects) { this.aspects = aspects; }

    public MetaDto getMeta() { return meta; }
    public void setMeta(MetaDto meta) { this.meta = meta; }
}
