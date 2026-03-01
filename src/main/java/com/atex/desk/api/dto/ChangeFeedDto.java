package com.atex.desk.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Content change feed response")
public class ChangeFeedDto
{
    @Schema(description = "Server time when query was executed (epoch milliseconds)")
    private long runTime;

    @Schema(description = "Current maximum commit ID (use as cursor for next poll)")
    private long maxCommitId;

    @Schema(description = "Total number of matching events returned")
    private int numFound;

    @Schema(description = "Number of events in this response")
    private int size;

    @Schema(description = "List of change events")
    private List<ChangeEventDto> events;

    public long getRunTime() { return runTime; }
    public void setRunTime(long runTime) { this.runTime = runTime; }

    public long getMaxCommitId() { return maxCommitId; }
    public void setMaxCommitId(long maxCommitId) { this.maxCommitId = maxCommitId; }

    public int getNumFound() { return numFound; }
    public void setNumFound(int numFound) { this.numFound = numFound; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }

    public List<ChangeEventDto> getEvents() { return events; }
    public void setEvents(List<ChangeEventDto> events) { this.events = events; }
}
