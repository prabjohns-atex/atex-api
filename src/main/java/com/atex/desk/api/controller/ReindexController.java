package com.atex.desk.api.controller;

import com.atex.desk.api.dto.ReindexJobDto;
import com.atex.desk.api.dto.ReindexRequestDto;
import com.atex.desk.api.entity.IndexerState;
import com.atex.desk.api.repository.IndexerStateRepository;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/admin/reindex")
@Tag(name = "Reindex", description = "SOLR reindex job management")
public class ReindexController
{
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;
    private final IndexerStateRepository indexerStateRepository;
    private final EntityManager entityManager;

    public ReindexController(IndexerStateRepository indexerStateRepository,
                             EntityManager entityManager) {
        this.indexerStateRepository = indexerStateRepository;
        this.entityManager = entityManager;
    }

    @PostMapping
    @Operation(summary = "Create a reindex job",
        description = "Submit a reindex request. Types: full (all content), filtered (by content type/date/partition), manual (list of content IDs).")
    @ApiResponse(responseCode = "201", description = "Reindex job created")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    public ResponseEntity<?> createReindexJob(@RequestBody ReindexRequestDto request) {
        if (request.getType() == null || request.getType().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "type is required"));
        }

        String type = request.getType().toLowerCase().trim();
        String jobType;
        String configJson;
        Long totalItems;

        switch (type) {
            case "full" -> {
                jobType = "REINDEX_FULL";
                configJson = null;
                totalItems = countLiveContent(null);
            }
            case "filtered" -> {
                jobType = "REINDEX_FILTERED";
                JsonObject config = new JsonObject();
                if (request.getContentTypes() != null && !request.getContentTypes().isEmpty()) {
                    JsonArray arr = new JsonArray();
                    request.getContentTypes().forEach(arr::add);
                    config.add("contentTypes", arr);
                }
                if (request.getDateFrom() != null) config.addProperty("dateFrom", request.getDateFrom());
                if (request.getDateTo() != null) config.addProperty("dateTo", request.getDateTo());
                if (request.getPartitions() != null && !request.getPartitions().isEmpty()) {
                    JsonArray arr = new JsonArray();
                    request.getPartitions().forEach(arr::add);
                    config.add("partitions", arr);
                }
                configJson = config.toString();
                totalItems = countLiveContent(config);
            }
            case "manual" -> {
                if (request.getContentIds() == null || request.getContentIds().isEmpty()) {
                    return ResponseEntity.badRequest().body(Map.of("error", "contentIds is required for manual reindex"));
                }
                jobType = "REINDEX_MANUAL";
                JsonObject config = new JsonObject();
                JsonArray arr = new JsonArray();
                request.getContentIds().forEach(arr::add);
                config.add("contentIds", arr);
                configJson = config.toString();
                totalItems = (long) request.getContentIds().size();
            }
            default -> {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid type: " + type + ". Must be full, filtered, or manual."));
            }
        }

        Instant now = Instant.now();
        String indexerId = "reindex-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneOffset.UTC).format(now);

        // Ensure unique ID if multiple requests in same second
        if (indexerStateRepository.findByIndexerId(indexerId).isPresent()) {
            indexerId = indexerId + "-" + (now.toEpochMilli() % 1000);
        }

        IndexerState state = new IndexerState();
        state.setIndexerId(indexerId);
        state.setJobType(jobType);
        state.setStatus("REQUESTED");
        state.setLastCursor(0);
        state.setConfig(configJson);
        state.setTotalItems(totalItems);
        state.setProcessedItems(0);
        state.setErrorCount(0);
        state.setCreatedAt(now);
        state.setUpdatedAt(now);

        indexerStateRepository.save(state);

        return ResponseEntity.status(HttpStatus.CREATED)
            .contentType(MediaType.APPLICATION_JSON)
            .body(toDto(state));
    }

    @GetMapping("/live")
    @Operation(summary = "Get live indexer status", description = "Returns the live (solr) indexer state for monitoring dashboards.")
    @ApiResponse(responseCode = "200", description = "Live indexer status")
    @ApiResponse(responseCode = "404", description = "Live indexer not found")
    public ResponseEntity<?> getLiveIndexerStatus() {
        return indexerStateRepository.findByIndexerId("solr")
            .map(s -> ResponseEntity.ok(toDto(s)))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    @Operation(summary = "List all reindex jobs", description = "Returns all reindex jobs ordered by creation time descending.")
    @ApiResponse(responseCode = "200", description = "Reindex jobs listed")
    public ResponseEntity<List<ReindexJobDto>> listReindexJobs() {
        List<IndexerState> jobs = indexerStateRepository.findAllReindexJobs();
        List<ReindexJobDto> dtos = jobs.stream().map(this::toDto).toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get reindex job status", description = "Returns a single reindex job with progress and ETA.")
    @ApiResponse(responseCode = "200", description = "Reindex job found")
    @ApiResponse(responseCode = "404", description = "Reindex job not found")
    public ResponseEntity<?> getReindexJob(@PathVariable String id) {
        Optional<IndexerState> state = indexerStateRepository.findByIndexerId(id);
        if (state.isEmpty() || "LIVE".equals(state.get().getJobType())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Reindex job not found: " + id));
        }
        return ResponseEntity.ok(toDto(state.get()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel or delete a reindex job",
        description = "If the job is active (REQUESTED/RUNNING), pauses it. If inactive (COMPLETED/FAILED/PAUSED), deletes it.")
    @ApiResponse(responseCode = "200", description = "Job paused")
    @ApiResponse(responseCode = "204", description = "Job deleted")
    @ApiResponse(responseCode = "404", description = "Job not found")
    public ResponseEntity<?> deleteReindexJob(@PathVariable String id) {
        Optional<IndexerState> opt = indexerStateRepository.findByIndexerId(id);
        if (opt.isEmpty() || "LIVE".equals(opt.get().getJobType())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Reindex job not found: " + id));
        }

        IndexerState state = opt.get();
        String status = state.getStatus();

        if ("REQUESTED".equals(status) || "RUNNING".equals(status)) {
            // Active job — pause it
            state.setStatus("PAUSED");
            state.setUpdatedAt(Instant.now());
            indexerStateRepository.save(state);
            return ResponseEntity.ok(toDto(state));
        } else {
            // Inactive job — delete it
            indexerStateRepository.delete(state);
            return ResponseEntity.noContent().build();
        }
    }

    @PostMapping("/pause")
    @Operation(summary = "Pause all indexing",
        description = "Pauses the live indexer and any active reindex jobs. Use for Solr maintenance.")
    @ApiResponse(responseCode = "200", description = "Indexing paused")
    public ResponseEntity<?> pauseAll() {
        int paused = 0;

        // Pause live indexer
        Optional<IndexerState> live = indexerStateRepository.findByIndexerId("solr");
        if (live.isPresent() && "RUNNING".equals(live.get().getStatus())) {
            live.get().setStatus("PAUSED");
            live.get().setUpdatedAt(Instant.now());
            indexerStateRepository.save(live.get());
            paused++;
        }

        // Pause active reindex jobs
        List<IndexerState> activeJobs = indexerStateRepository.findByJobTypeInAndStatusIn(
            List.of("REINDEX_FULL", "REINDEX_FILTERED", "REINDEX_MANUAL"),
            List.of("REQUESTED", "RUNNING"));
        for (IndexerState job : activeJobs) {
            job.setStatus("PAUSED");
            job.setUpdatedAt(Instant.now());
            indexerStateRepository.save(job);
            paused++;
        }

        return ResponseEntity.ok(Map.of("paused", paused, "message", "All indexing paused"));
    }

    @PostMapping("/resume")
    @Operation(summary = "Resume all indexing",
        description = "Resumes the live indexer and any paused reindex jobs.")
    @ApiResponse(responseCode = "200", description = "Indexing resumed")
    public ResponseEntity<?> resumeAll() {
        int resumed = 0;

        // Resume live indexer
        Optional<IndexerState> live = indexerStateRepository.findByIndexerId("solr");
        if (live.isPresent() && "PAUSED".equals(live.get().getStatus())) {
            live.get().setStatus("RUNNING");
            live.get().setUpdatedAt(Instant.now());
            indexerStateRepository.save(live.get());
            resumed++;
        }

        // Resume paused reindex jobs
        List<IndexerState> pausedJobs = indexerStateRepository.findByJobTypeInAndStatusIn(
            List.of("REINDEX_FULL", "REINDEX_FILTERED", "REINDEX_MANUAL"),
            List.of("PAUSED"));
        for (IndexerState job : pausedJobs) {
            job.setStatus("RUNNING");
            job.setUpdatedAt(Instant.now());
            indexerStateRepository.save(job);
            resumed++;
        }

        return ResponseEntity.ok(Map.of("resumed", resumed, "message", "All indexing resumed"));
    }

    // ========================
    // DTO conversion
    // ========================

    private ReindexJobDto toDto(IndexerState state) {
        ReindexJobDto dto = new ReindexJobDto();
        dto.setId(state.getIndexerId());
        dto.setType(state.getJobType());
        dto.setStatus(state.getStatus());
        dto.setLockedBy(state.getLockedBy());

        if (state.getCreatedAt() != null) dto.setCreatedAt(ISO.format(state.getCreatedAt()));
        if (state.getUpdatedAt() != null) dto.setUpdatedAt(ISO.format(state.getUpdatedAt()));

        // Progress
        ReindexJobDto.Progress progress = new ReindexJobDto.Progress();
        progress.setProcessed(state.getProcessedItems());
        progress.setTotal(state.getTotalItems());
        if (state.getTotalItems() != null && state.getTotalItems() > 0) {
            progress.setPercent(Math.round(state.getProcessedItems() * 1000.0 / state.getTotalItems()) / 10.0);
        }
        dto.setProgress(progress);

        // ETA
        if (state.getStartedAt() != null && state.getProcessedItems() > 0 && state.getTotalItems() != null && state.getTotalItems() > 0) {
            ReindexJobDto.Eta eta = new ReindexJobDto.Eta();
            eta.setStartedAt(ISO.format(state.getStartedAt()));

            Instant now = Instant.now();
            long elapsed = now.getEpochSecond() - state.getStartedAt().getEpochSecond();
            eta.setElapsedSeconds(elapsed);

            if (elapsed > 0) {
                double rate = (double) state.getProcessedItems() / elapsed;
                long remaining = state.getTotalItems() - state.getProcessedItems();
                long estimatedRemaining = (long) (remaining / rate);
                eta.setEstimatedRemainingSeconds(estimatedRemaining);
                eta.setEstimatedCompletionTime(ISO.format(now.plusSeconds(estimatedRemaining)));
            }
            dto.setEta(eta);
        }

        // Errors
        ReindexJobDto.Errors errors = new ReindexJobDto.Errors();
        errors.setCount(state.getErrorCount());
        errors.setLastError(state.getErrorMessage());
        dto.setErrors(errors);

        // Config
        if (state.getConfig() != null) {
            try {
                dto.setConfig(JsonParser.parseString(state.getConfig()));
            } catch (Exception e) {
                dto.setConfig(state.getConfig());
            }
        }

        return dto;
    }

    // ========================
    // Count helpers
    // ========================

    /**
     * Count live content, optionally filtered.
     */
    private Long countLiveContent(JsonObject config) {
        try {
            StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM idversions iv "
                + "JOIN idviews ivw ON iv.versionid = ivw.versionid "
                + "JOIN views v ON v.viewid = ivw.viewid "
                + "WHERE v.name = 'p.latest' ");

            if (config != null) {
                if (config.has("contentTypes")) {
                    sql.append("AND EXISTS (SELECT 1 FROM contents c WHERE c.versionid = iv.versionid AND c.contenttype IN :contentTypes) ");
                }
                if (config.has("dateFrom")) {
                    sql.append("AND iv.created_at >= :dateFrom ");
                }
                if (config.has("dateTo")) {
                    sql.append("AND iv.created_at <= :dateTo ");
                }
            }

            var query = entityManager.createNativeQuery(sql.toString());

            if (config != null) {
                if (config.has("contentTypes")) {
                    List<String> types = new java.util.ArrayList<>();
                    config.getAsJsonArray("contentTypes").forEach(e -> types.add(e.getAsString()));
                    query.setParameter("contentTypes", types);
                }
                if (config.has("dateFrom")) {
                    query.setParameter("dateFrom", Instant.parse(config.get("dateFrom").getAsString()));
                }
                if (config.has("dateTo")) {
                    query.setParameter("dateTo", Instant.parse(config.get("dateTo").getAsString()));
                }
            }

            Number count = (Number) query.getSingleResult();
            return count.longValue();
        } catch (Exception e) {
            return null;
        }
    }
}

