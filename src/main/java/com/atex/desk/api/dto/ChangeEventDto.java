package com.atex.desk.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "A content change event")
public class ChangeEventDto
{
    @Schema(description = "Unversioned content ID", example = "onecms:abc123")
    private String contentId;

    @Schema(description = "Versioned content ID", example = "onecms:abc123:v1")
    private String contentVersionId;

    @Schema(description = "Monotonically increasing commit ID (cursor)", example = "104")
    private long commitId;

    @Schema(description = "Time of commit (epoch milliseconds)", example = "1582035880353")
    private long commitTime;

    @Schema(description = "Event type: CREATE, UPDATE, or DELETE", example = "CREATE")
    private String eventType;

    @Schema(description = "Object type: image, article, page, etc.", example = "image")
    private String objectType;

    @Schema(description = "Content type (aspect class name)", example = "atex.onecms.image")
    private String contentType;

    @Schema(description = "Content creation time (epoch milliseconds)")
    private long creationTime;

    @Schema(description = "Creator user ID", example = "98")
    private String creator;

    @Schema(description = "Last modification time (epoch milliseconds)")
    private long modificationTime;

    @Schema(description = "Modifier user ID", example = "98")
    private String modifier;

    @Schema(description = "Security parent content ID")
    private String securityParentId;

    @Schema(description = "Insertion parent content ID")
    private String insertionParentId;

    @Schema(description = "Content partitions")
    private List<String> partitions;

    public String getContentId() { return contentId; }
    public void setContentId(String contentId) { this.contentId = contentId; }

    public String getContentVersionId() { return contentVersionId; }
    public void setContentVersionId(String contentVersionId) { this.contentVersionId = contentVersionId; }

    public long getCommitId() { return commitId; }
    public void setCommitId(long commitId) { this.commitId = commitId; }

    public long getCommitTime() { return commitTime; }
    public void setCommitTime(long commitTime) { this.commitTime = commitTime; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getObjectType() { return objectType; }
    public void setObjectType(String objectType) { this.objectType = objectType; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public long getCreationTime() { return creationTime; }
    public void setCreationTime(long creationTime) { this.creationTime = creationTime; }

    public String getCreator() { return creator; }
    public void setCreator(String creator) { this.creator = creator; }

    public long getModificationTime() { return modificationTime; }
    public void setModificationTime(long modificationTime) { this.modificationTime = modificationTime; }

    public String getModifier() { return modifier; }
    public void setModifier(String modifier) { this.modifier = modifier; }

    public String getSecurityParentId() { return securityParentId; }
    public void setSecurityParentId(String securityParentId) { this.securityParentId = securityParentId; }

    public String getInsertionParentId() { return insertionParentId; }
    public void setInsertionParentId(String insertionParentId) { this.insertionParentId = insertionParentId; }

    public List<String> getPartitions() { return partitions; }
    public void setPartitions(List<String> partitions) { this.partitions = partitions; }
}
