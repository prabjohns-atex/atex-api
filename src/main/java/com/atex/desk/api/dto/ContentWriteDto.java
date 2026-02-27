package com.atex.desk.api.dto;

import java.util.Map;

/**
 * Matches the OneCMS Content JSON structure for create/update requests.
 */
public class ContentWriteDto
{
    private String id;
    private String version;
    private Map<String, AspectDto> aspects;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public Map<String, AspectDto> getAspects() { return aspects; }
    public void setAspects(Map<String, AspectDto> aspects) { this.aspects = aspects; }
}
