package com.atex.desk.api.indexing;

import com.atex.desk.api.entity.ChangeListEntry;
import com.atex.desk.api.entity.IndexerState;
import com.atex.desk.api.repository.IndexerStateRepository;
import com.atex.onecms.app.dam.solr.SolrService;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.IdUtil;
import com.atex.onecms.content.Subject;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.apache.solr.common.SolrInputDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Background SOLR indexing processor that decouples indexing from content writes.
 *
 * <p>Two scheduled threads:
 * <ul>
 *   <li><b>Live indexer</b> — polls the changelist table by cursor, indexes new/updated content</li>
 *   <li><b>Reindex worker</b> — processes reindex jobs (full, filtered, manual) from indexer_state</li>
 * </ul>
 *
 * <p>Multi-instance coordination via lease-based row locking on the indexer_state table.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "desk.indexing.enabled", havingValue = "true", matchIfMissing = true)
public class SolrIndexProcessor
{
    private static final Logger LOG = Logger.getLogger(SolrIndexProcessor.class.getName());

    private static final String LIVE_INDEXER_ID = "solr";
    private static final List<String> REINDEX_JOB_TYPES = List.of("REINDEX_FULL", "REINDEX_FILTERED", "REINDEX_MANUAL");
    private static final List<String> ACTIONABLE_STATUSES = List.of("REQUESTED", "RUNNING");

    private final IndexerStateRepository indexerStateRepository;
    private final ContentManager contentManager;
    private final DamIndexComposer damIndexComposer;
    private final SolrService solrService;
    private final EntityManager entityManager;
    private final Gson gson = new Gson();

    private final String instanceId;
    private final int batchSize;
    private final int leaseSeconds;
    private final String collection;

    public SolrIndexProcessor(IndexerStateRepository indexerStateRepository,
                              @Nullable ContentManager contentManager,
                              DamIndexComposer damIndexComposer,
                              @Nullable SolrService solrService,
                              EntityManager entityManager,
                              @Value("${desk.indexing.instance-id:#{T(java.net.InetAddress).getLocalHost().getHostName()}}") String instanceId,
                              @Value("${desk.indexing.batch-size:100}") int batchSize,
                              @Value("${desk.indexing.lease-seconds:60}") int leaseSeconds,
                              @Value("${desk.solr-core:onecms}") String collection) {
        this.indexerStateRepository = indexerStateRepository;
        this.contentManager = contentManager;
        this.damIndexComposer = damIndexComposer;
        this.solrService = solrService;
        this.entityManager = entityManager;
        this.instanceId = instanceId;
        this.batchSize = batchSize;
        this.leaseSeconds = leaseSeconds;
        this.collection = collection;
    }

    // ========================
    // Live indexer — polls changelist
    // ========================

    @Scheduled(fixedDelayString = "${desk.indexing.poll-interval:2000}")
    public void processLiveIndex() {
        if (solrService == null || contentManager == null) return;

        try {
            IndexerState state = indexerStateRepository.findByIndexerId(LIVE_INDEXER_ID).orElse(null);
            if (state == null) return;
            if ("PAUSED".equals(state.getStatus())) return;
            if (!acquireLease(state)) return;

            try {
                processLiveBatch(state);
            } finally {
                releaseLease(state);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Live indexer tick failed", e);
        }
    }

    private void processLiveBatch(IndexerState state) {
        long cursor = state.getLastCursor();

        @SuppressWarnings("unchecked")
        List<ChangeListEntry> entries = entityManager.createQuery(
                "SELECT c FROM ChangeListEntry c WHERE c.id > :cursor ORDER BY c.id ASC")
            .setParameter("cursor", (int) cursor)
            .setMaxResults(batchSize)
            .getResultList();

        if (entries.isEmpty()) return;

        List<SolrInputDocument> docsToIndex = new ArrayList<>();
        List<String> idsToDelete = new ArrayList<>();
        int highestId = (int) cursor;

        for (ChangeListEntry entry : entries) {
            try {
                if (isDeleteEvent(entry.getEventtype())) {
                    idsToDelete.add(entry.getContentid());
                } else {
                    SolrInputDocument doc = composeDocument(entry.getContentid());
                    if (doc != null) {
                        docsToIndex.add(doc);
                    }
                }
                highestId = Math.max(highestId, entry.getId());
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to compose document for " + entry.getContentid(), e);
                highestId = Math.max(highestId, entry.getId());
            }
        }

        try {
            if (!docsToIndex.isEmpty()) {
                solrService.indexBatch(collection, docsToIndex);
            }
            if (!idsToDelete.isEmpty()) {
                solrService.deleteBatch(collection, idsToDelete);
            }

            state.setLastCursor(highestId);
            state.setUpdatedAt(Instant.now());
            indexerStateRepository.save(state);

            final int processedCursor = highestId;
            LOG.fine(() -> "Live indexer: processed " + entries.size() + " entries, cursor now at " + processedCursor);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Live indexer: Solr batch failed, cursor stays at " + cursor, e);
        }
    }

    // ========================
    // Reindex worker — processes reindex jobs
    // ========================

    @Scheduled(fixedDelayString = "${desk.indexing.reindex-poll-interval:5000}")
    public void processReindexJobs() {
        if (solrService == null || contentManager == null) return;

        try {
            List<IndexerState> jobs = indexerStateRepository.findByJobTypeInAndStatusIn(
                REINDEX_JOB_TYPES, ACTIONABLE_STATUSES);
            if (jobs.isEmpty()) return;

            IndexerState job = jobs.getFirst();

            // Check if paused between ticks
            if ("PAUSED".equals(job.getStatus())) return;

            if (!acquireLease(job)) return;

            try {
                processReindexBatch(job);
            } finally {
                releaseLease(job);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Reindex worker tick failed", e);
        }
    }

    private void processReindexBatch(IndexerState job) {
        // Mark as RUNNING on first processing
        if ("REQUESTED".equals(job.getStatus())) {
            job.setStatus("RUNNING");
            job.setStartedAt(Instant.now());
            indexerStateRepository.save(job);
        }

        try {
            boolean completed = switch (job.getJobType()) {
                case "REINDEX_FULL" -> processFullReindex(job);
                case "REINDEX_FILTERED" -> processFilteredReindex(job);
                case "REINDEX_MANUAL" -> processManualReindex(job);
                default -> {
                    LOG.warning("Unknown reindex job type: " + job.getJobType());
                    yield true;
                }
            };

            if (completed) {
                job.setStatus("COMPLETED");
                job.setUpdatedAt(Instant.now());
                indexerStateRepository.save(job);
                LOG.info("Reindex job completed: " + job.getIndexerId()
                    + " (" + job.getProcessedItems() + " items)");
            }
        } catch (Exception e) {
            job.setErrorCount(job.getErrorCount() + 1);
            job.setErrorMessage(e.getMessage() != null ? truncate(e.getMessage(), 2000) : e.getClass().getName());
            job.setUpdatedAt(Instant.now());
            indexerStateRepository.save(job);
            LOG.log(Level.WARNING, "Reindex batch failed for " + job.getIndexerId()
                + " (error #" + job.getErrorCount() + "), will retry", e);
        }
    }

    /**
     * Full reindex: walks all live content via idversions + idviews (p.latest), cursor on versionid.
     * Returns true when all content has been processed.
     */
    private boolean processFullReindex(IndexerState job) throws Exception {
        long cursor = job.getLastCursor();

        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(
                "SELECT iv.versionid, iv.idtype, iv.id, iv.version "
                + "FROM idversions iv "
                + "JOIN idviews ivw ON iv.versionid = ivw.versionid "
                + "JOIN views v ON v.viewid = ivw.viewid "
                + "WHERE v.name = 'p.latest' AND iv.versionid > :cursor "
                + "ORDER BY iv.versionid ASC")
            .setParameter("cursor", cursor)
            .setMaxResults(batchSize)
            .getResultList();

        if (rows.isEmpty()) return true;

        return indexVersionRows(job, rows);
    }

    /**
     * Filtered reindex: walks live content matching filter criteria from config JSON.
     * Supported filters: contentTypes, dateFrom, dateTo, partitions.
     * Returns true when all matching content has been processed.
     */
    private boolean processFilteredReindex(IndexerState job) throws Exception {
        long cursor = job.getLastCursor();
        JsonObject config = gson.fromJson(job.getConfig(), JsonObject.class);

        StringBuilder sql = new StringBuilder(
            "SELECT iv.versionid, iv.idtype, iv.id, iv.version "
            + "FROM idversions iv "
            + "JOIN idviews ivw ON iv.versionid = ivw.versionid "
            + "JOIN views v ON v.viewid = ivw.viewid "
            + "JOIN contents c ON c.versionid = iv.versionid "
            + "WHERE v.name = 'p.latest' AND iv.versionid > :cursor ");

        // Content type filter
        List<String> contentTypes = jsonArrayToList(config, "contentTypes");
        if (!contentTypes.isEmpty()) {
            sql.append("AND c.contenttype IN :contentTypes ");
        }

        // Date range filters
        String dateFrom = jsonString(config, "dateFrom");
        if (dateFrom != null) {
            sql.append("AND iv.created_at >= :dateFrom ");
        }
        String dateTo = jsonString(config, "dateTo");
        if (dateTo != null) {
            sql.append("AND iv.created_at <= :dateTo ");
        }

        sql.append("ORDER BY iv.versionid ASC");

        Query query = entityManager.createNativeQuery(sql.toString());
        query.setParameter("cursor", cursor);
        query.setMaxResults(batchSize);

        if (!contentTypes.isEmpty()) {
            query.setParameter("contentTypes", contentTypes);
        }
        if (dateFrom != null) {
            query.setParameter("dateFrom", Instant.parse(dateFrom));
        }
        if (dateTo != null) {
            query.setParameter("dateTo", Instant.parse(dateTo));
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        if (rows.isEmpty()) return true;

        return indexVersionRows(job, rows);
    }

    /**
     * Manual reindex: iterates a list of content IDs stored in config JSON.
     * last_cursor is the array index.
     * Returns true when all IDs have been processed.
     */
    private boolean processManualReindex(IndexerState job) throws Exception {
        JsonObject config = gson.fromJson(job.getConfig(), JsonObject.class);
        JsonArray contentIds = config.has("contentIds") ? config.getAsJsonArray("contentIds") : new JsonArray();

        int startIndex = (int) job.getLastCursor();
        int endIndex = Math.min(startIndex + batchSize, contentIds.size());

        if (startIndex >= contentIds.size()) return true;

        List<SolrInputDocument> docsToIndex = new ArrayList<>();
        int processed = 0;

        for (int i = startIndex; i < endIndex; i++) {
            String contentIdStr = contentIds.get(i).getAsString();
            try {
                ContentId contentId = IdUtil.fromString(contentIdStr);
                ContentVersionId vid = contentManager.resolve(contentId, Subject.NOBODY_CALLER);
                if (vid != null) {
                    @SuppressWarnings("unchecked")
                    ContentResult<Object> result = (ContentResult<Object>) contentManager.get(
                        vid, null, Object.class, null, Subject.NOBODY_CALLER);
                    if (result.getStatus().isSuccess() && result.getContent() != null) {
                        JsonObject solrDoc = damIndexComposer.compose(result, vid);
                        docsToIndex.add(jsonToSolrDoc(solrDoc));
                    }
                }
                processed++;
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to compose document for manual reindex: " + contentIdStr, e);
                processed++;
            }
        }

        if (!docsToIndex.isEmpty()) {
            solrService.indexBatch(collection, docsToIndex);
        }

        job.setLastCursor(endIndex);
        job.setProcessedItems(job.getProcessedItems() + processed);
        job.setUpdatedAt(Instant.now());
        indexerStateRepository.save(job);

        return endIndex >= contentIds.size();
    }

    // ========================
    // Shared helpers
    // ========================

    /**
     * Index a batch of version rows (from full or filtered reindex queries).
     * Each row is [versionid, idtype, id, version].
     * Returns true if this was the last batch (fewer rows than batch size).
     */
    private boolean indexVersionRows(IndexerState job, List<Object[]> rows) throws Exception {
        List<SolrInputDocument> docsToIndex = new ArrayList<>();
        long highestVersionId = job.getLastCursor();
        int processed = 0;

        for (Object[] row : rows) {
            int versionId = ((Number) row[0]).intValue();
            String id = (String) row[2];
            String version = (String) row[3];

            try {
                // Build versioned content ID and fetch content
                ContentId contentId = new ContentId("onecms", id);
                ContentVersionId vid = new ContentVersionId(contentId, version);

                @SuppressWarnings("unchecked")
                ContentResult<Object> result = (ContentResult<Object>) contentManager.get(
                    vid, null, Object.class, null, Subject.NOBODY_CALLER);

                if (result.getStatus().isSuccess() && result.getContent() != null) {
                    JsonObject solrDoc = damIndexComposer.compose(result, vid);
                    docsToIndex.add(jsonToSolrDoc(solrDoc));
                }
                processed++;
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to compose document for versionId=" + versionId
                    + " id=" + id, e);
                processed++;
            }
            highestVersionId = Math.max(highestVersionId, versionId);
        }

        if (!docsToIndex.isEmpty()) {
            solrService.indexBatch(collection, docsToIndex);
        }

        job.setLastCursor(highestVersionId);
        job.setProcessedItems(job.getProcessedItems() + processed);
        job.setUpdatedAt(Instant.now());
        indexerStateRepository.save(job);

        return rows.size() < batchSize;
    }

    /**
     * Compose a Solr document for a content ID by resolving and fetching it.
     */
    private SolrInputDocument composeDocument(String contentKey) {
        try {
            ContentId contentId = new ContentId("onecms", contentKey);
            ContentVersionId vid = contentManager.resolve(contentId, Subject.NOBODY_CALLER);
            if (vid == null) return null;

            @SuppressWarnings("unchecked")
            ContentResult<Object> result = (ContentResult<Object>) contentManager.get(
                vid, null, Object.class, null, Subject.NOBODY_CALLER);

            if (!result.getStatus().isSuccess() || result.getContent() == null) return null;

            JsonObject solrDoc = damIndexComposer.compose(result, vid);
            return jsonToSolrDoc(solrDoc);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to compose document for " + contentKey, e);
            return null;
        }
    }

    /**
     * Convert a Gson JsonObject to a SolrInputDocument.
     */
    private SolrInputDocument jsonToSolrDoc(JsonObject json) {
        SolrInputDocument doc = new SolrInputDocument();
        for (var entry : json.entrySet()) {
            String field = entry.getKey();
            var value = entry.getValue();
            if (value.isJsonNull()) continue;
            if (value.isJsonPrimitive()) {
                var prim = value.getAsJsonPrimitive();
                if (prim.isBoolean()) {
                    doc.addField(field, prim.getAsBoolean());
                } else if (prim.isNumber()) {
                    // Convert Gson LazilyParsedNumber to a real Long/Double
                    // to avoid "com.google.gson.internal.LazilyParsedNumber:N" in Solr
                    Number num = prim.getAsNumber();
                    String numStr = num.toString();
                    if (numStr.contains(".") || numStr.contains("e") || numStr.contains("E")) {
                        doc.addField(field, num.doubleValue());
                    } else {
                        doc.addField(field, num.longValue());
                    }
                } else {
                    doc.addField(field, prim.getAsString());
                }
            } else if (value.isJsonArray()) {
                for (var el : value.getAsJsonArray()) {
                    if (el.isJsonPrimitive()) {
                        doc.addField(field, el.getAsString());
                    }
                }
            } else {
                doc.addField(field, gson.toJson(value));
            }
        }
        return doc;
    }

    /**
     * Check if an event type ID corresponds to a DELETE event.
     * Event type IDs: CREATE=1, UPDATE=2, REMOVE=3, ..., DELETE=8
     */
    private boolean isDeleteEvent(int eventTypeId) {
        // DELETE and REMOVE events trigger index removal
        return eventTypeId == 3 || eventTypeId == 8;
    }

    // ========================
    // Lease management
    // ========================

    private boolean acquireLease(IndexerState state) {
        Instant now = Instant.now();

        if (state.getLockedBy() != null && state.getLockedAt() != null) {
            long elapsed = now.getEpochSecond() - state.getLockedAt().getEpochSecond();
            if (elapsed < leaseSeconds) {
                // Lease is held by another instance and not expired
                if (!instanceId.equals(state.getLockedBy())) {
                    return false;
                }
                // Same instance — renew
            }
        }

        state.setLockedBy(instanceId);
        state.setLockedAt(now);
        indexerStateRepository.save(state);
        return true;
    }

    private void releaseLease(IndexerState state) {
        state.setLockedBy(null);
        state.setLockedAt(null);
        state.setUpdatedAt(Instant.now());
        indexerStateRepository.save(state);
    }

    // ========================
    // JSON helpers
    // ========================

    private List<String> jsonArrayToList(JsonObject config, String field) {
        List<String> result = new ArrayList<>();
        if (config != null && config.has(field) && config.get(field).isJsonArray()) {
            for (var el : config.getAsJsonArray(field)) {
                if (el.isJsonPrimitive()) {
                    result.add(el.getAsString());
                }
            }
        }
        return result;
    }

    private String jsonString(JsonObject config, String field) {
        if (config != null && config.has(field) && config.get(field).isJsonPrimitive()) {
            return config.get(field).getAsString();
        }
        return null;
    }

    private static String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}

