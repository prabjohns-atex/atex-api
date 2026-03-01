package com.atex.desk.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Group info")
public class GroupDto
{
    @Schema(description = "The group ID")
    private String groupId;

    @Schema(description = "The group name")
    private String name;

    @Schema(description = "Group creation time (epoch milliseconds)")
    private String createdAt;

    @Schema(description = "Member user IDs (only present when requested)")
    private List<String> members;

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public List<String> getMembers() { return members; }
    public void setMembers(List<String> members) { this.members = members; }
}
