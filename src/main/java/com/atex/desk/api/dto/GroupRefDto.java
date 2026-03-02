package com.atex.desk.api.dto;

/**
 * Group reference within a user response, matching reference OneCMS format.
 */
public class GroupRefDto {

    private String type = "group";
    private String id;
    private String name;
    private String principalId;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPrincipalId() { return principalId; }
    public void setPrincipalId(String principalId) { this.principalId = principalId; }
}
