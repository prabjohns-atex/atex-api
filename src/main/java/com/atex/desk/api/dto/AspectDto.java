package com.atex.desk.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Matches the OneCMS Aspect JSON structure within a ContentResult.
 */
@Schema(description = "An aspect, the data field contains a _type field with the aspect type")
public class AspectDto
{
    @Schema(description = "The name of the aspect")
    private String name;

    @Schema(description = "The versioned contentId of the aspect")
    private String version;

    @Schema(description = "The aspect data, contains a _type field with the aspect type")
    private Map<String, Object> data;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }
}
