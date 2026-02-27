package com.atex.desk.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Workspace info response DTO.
 */
@Schema(description = "Workspace information")
public class WorkspaceInfoDto {

    @Schema(description = "The workspace id")
    private String workspaceId;

    @Schema(description = "Number of drafts in the workspace")
    private int draftCount;

    @Schema(description = "Workspace creation time")
    private Instant createdAt;

    public WorkspaceInfoDto() {}

    public WorkspaceInfoDto(String workspaceId, int draftCount, Instant createdAt) {
        this.workspaceId = workspaceId;
        this.draftCount = draftCount;
        this.createdAt = createdAt;
    }

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }

    public int getDraftCount() { return draftCount; }
    public void setDraftCount(int draftCount) { this.draftCount = draftCount; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
