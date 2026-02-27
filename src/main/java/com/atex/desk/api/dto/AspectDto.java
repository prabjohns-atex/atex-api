package com.atex.desk.api.dto;

import java.util.Map;

/**
 * Matches the OneCMS Aspect JSON structure within a ContentResult.
 */
public class AspectDto
{
    private String name;
    private String version;
    private Map<String, Object> data;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }
}
