package com.atex.desk.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "indexer_state")
public class IndexerState
{
    @Id
    @Column(name = "indexer_id", length = 64)
    private String indexerId;

    @Column(name = "job_type", length = 32, nullable = false)
    private String jobType;

    @Column(length = 16, nullable = false)
    private String status;

    @Column(name = "last_cursor", nullable = false)
    private long lastCursor;

    @Column(columnDefinition = "JSON")
    private String config;

    @Column(name = "total_items")
    private Long totalItems;

    @Column(name = "processed_items", nullable = false)
    private long processedItems;

    @Column(name = "error_count", nullable = false)
    private int errorCount;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "locked_by")
    private String lockedBy;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public String getIndexerId() { return indexerId; }
    public void setIndexerId(String indexerId) { this.indexerId = indexerId; }

    public String getJobType() { return jobType; }
    public void setJobType(String jobType) { this.jobType = jobType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getLastCursor() { return lastCursor; }
    public void setLastCursor(long lastCursor) { this.lastCursor = lastCursor; }

    public String getConfig() { return config; }
    public void setConfig(String config) { this.config = config; }

    public Long getTotalItems() { return totalItems; }
    public void setTotalItems(Long totalItems) { this.totalItems = totalItems; }

    public long getProcessedItems() { return processedItems; }
    public void setProcessedItems(long processedItems) { this.processedItems = processedItems; }

    public int getErrorCount() { return errorCount; }
    public void setErrorCount(int errorCount) { this.errorCount = errorCount; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getLockedBy() { return lockedBy; }
    public void setLockedBy(String lockedBy) { this.lockedBy = lockedBy; }

    public Instant getLockedAt() { return lockedAt; }
    public void setLockedAt(Instant lockedAt) { this.lockedAt = lockedAt; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

