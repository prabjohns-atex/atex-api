package com.atex.desk.api.dto;

import java.time.Instant;

/**
 * Workspace info response DTO.
 */
public class WorkspaceInfoDto {

    private String workspaceId;
    private int draftCount;
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
