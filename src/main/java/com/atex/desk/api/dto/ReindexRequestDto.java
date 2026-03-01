package com.atex.desk.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Reindex job request")
public class ReindexRequestDto
{
    @Schema(description = "Reindex type: full, filtered, or manual", required = true, example = "full")
    private String type;

    @Schema(description = "Content type filter (for filtered reindex)", example = "[\"com.atex.standard.image.ImageContentDataBean\"]")
    private List<String> contentTypes;

    @Schema(description = "Date from filter (ISO-8601, for filtered reindex)", example = "2026-01-01T00:00:00Z")
    private String dateFrom;

    @Schema(description = "Date to filter (ISO-8601, for filtered reindex)", example = "2026-03-01T00:00:00Z")
    private String dateTo;

    @Schema(description = "Partition filter (for filtered reindex)")
    private List<String> partitions;

    @Schema(description = "Content IDs to reindex (for manual reindex)", example = "[\"onecms:abc123\", \"onecms:def456\"]")
    private List<String> contentIds;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public List<String> getContentTypes() { return contentTypes; }
    public void setContentTypes(List<String> contentTypes) { this.contentTypes = contentTypes; }

    public String getDateFrom() { return dateFrom; }
    public void setDateFrom(String dateFrom) { this.dateFrom = dateFrom; }

    public String getDateTo() { return dateTo; }
    public void setDateTo(String dateTo) { this.dateTo = dateTo; }

    public List<String> getPartitions() { return partitions; }
    public void setPartitions(List<String> partitions) { this.partitions = partitions; }

    public List<String> getContentIds() { return contentIds; }
    public void setContentIds(List<String> contentIds) { this.contentIds = contentIds; }
}

