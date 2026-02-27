package com.atex.desk.api.dto;

import java.util.Map;

/**
 * Matches the OneCMS ContentResult JSON structure.
 * Extends the Content structure with version metadata.
 */
public class ContentResultDto
{
    private String id;
    private String version;
    private Map<String, AspectDto> aspects;
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
