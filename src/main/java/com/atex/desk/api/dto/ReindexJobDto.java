package com.atex.desk.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Reindex job status and progress")
public class ReindexJobDto
{
    @Schema(description = "Job identifier", example = "reindex-20260301-143000")
    private String id;

    @Schema(description = "Job type: REINDEX_FULL, REINDEX_FILTERED, or REINDEX_MANUAL")
    private String type;

    @Schema(description = "Job status: REQUESTED, RUNNING, PAUSED, COMPLETED, or FAILED")
    private String status;

    @Schema(description = "Progress information")
    private Progress progress;

    @Schema(description = "ETA information (null if not yet started)")
    private Eta eta;

    @Schema(description = "Error information")
    private Errors errors;

    @Schema(description = "Job configuration (filters or content ID list)")
    private Object config;

    @Schema(description = "Instance currently processing this job")
    private String lockedBy;

    @Schema(description = "Job creation time (ISO-8601)")
    private String createdAt;

    @Schema(description = "Last update time (ISO-8601)")
    private String updatedAt;

    @Schema(description = "Progress details")
    public static class Progress
    {
        @Schema(description = "Number of items processed so far")
        private long processed;

        @Schema(description = "Total number of items to process")
        private Long total;

        @Schema(description = "Completion percentage (0-100)")
        private Double percent;

        public long getProcessed() { return processed; }
        public void setProcessed(long processed) { this.processed = processed; }

        public Long getTotal() { return total; }
        public void setTotal(Long total) { this.total = total; }

        public Double getPercent() { return percent; }
        public void setPercent(Double percent) { this.percent = percent; }
    }

    @Schema(description = "Estimated time of completion")
    public static class Eta
    {
        @Schema(description = "Time when processing started (ISO-8601)")
        private String startedAt;

        @Schema(description = "Elapsed time in seconds since processing started")
        private Long elapsedSeconds;

        @Schema(description = "Estimated remaining time in seconds")
        private Long estimatedRemainingSeconds;

        @Schema(description = "Estimated completion time (ISO-8601)")
        private String estimatedCompletionTime;

        public String getStartedAt() { return startedAt; }
        public void setStartedAt(String startedAt) { this.startedAt = startedAt; }

        public Long getElapsedSeconds() { return elapsedSeconds; }
        public void setElapsedSeconds(Long elapsedSeconds) { this.elapsedSeconds = elapsedSeconds; }

        public Long getEstimatedRemainingSeconds() { return estimatedRemainingSeconds; }
        public void setEstimatedRemainingSeconds(Long estimatedRemainingSeconds) { this.estimatedRemainingSeconds = estimatedRemainingSeconds; }

        public String getEstimatedCompletionTime() { return estimatedCompletionTime; }
        public void setEstimatedCompletionTime(String estimatedCompletionTime) { this.estimatedCompletionTime = estimatedCompletionTime; }
    }

    @Schema(description = "Error details")
    public static class Errors
    {
        @Schema(description = "Total error count")
        private int count;

        @Schema(description = "Most recent error message")
        private String lastError;

        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }

        public String getLastError() { return lastError; }
        public void setLastError(String lastError) { this.lastError = lastError; }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Progress getProgress() { return progress; }
    public void setProgress(Progress progress) { this.progress = progress; }

    public Eta getEta() { return eta; }
    public void setEta(Eta eta) { this.eta = eta; }

    public Errors getErrors() { return errors; }
    public void setErrors(Errors errors) { this.errors = errors; }

    public Object getConfig() { return config; }
    public void setConfig(Object config) { this.config = config; }

    public String getLockedBy() { return lockedBy; }
    public void setLockedBy(String lockedBy) { this.lockedBy = lockedBy; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}

