package com.atex.onecms.content;

/**
 * Bean for configuration content that stores JSON data.
 * Matches the Polopoly p.ConfigurationData content type structure.
 *
 * Fields:
 *   name      - display name of the configuration
 *   json      - the configuration as a parsed JSON string (normalized, no comments)
 *   dataType  - source format type (e.g. "json")
 *   dataValue - the raw source configuration (may include comments/formatting)
 */
public class ConfigurationDataBean {

    private String name;
    private String json;
    private String dataType;
    private String dataValue;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getJson() { return json; }
    public void setJson(String json) { this.json = json; }

    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }

    public String getDataValue() { return dataValue; }
    public void setDataValue(String dataValue) { this.dataValue = dataValue; }
}
