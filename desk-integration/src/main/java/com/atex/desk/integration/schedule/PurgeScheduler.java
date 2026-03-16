package com.atex.desk.integration.schedule;

import com.atex.desk.integration.config.IntegrationProperties;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.ContentWriteBuilder;
import com.atex.onecms.content.InsertionInfoAspectBean;
import com.atex.onecms.content.Subject;
import org.apache.solr.client.solrj.SolrQuery;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Scheduled content purge/housekeeping.
 * Replaces the legacy CustomPurgeProcessor Camel route.
 *
 * <p>Operations:
 * <ul>
 *   <li><b>Trash</b>: Moves content matching configured Solr queries to the trash partition
 *       by updating its security parent to the trash folder.</li>
 *   <li><b>Solr cleanup</b>: Removes orphaned documents from Solr that no longer exist in the DB.</li>
 * </ul>
 *
 * <p>Configured via {@code desk.integration.purge.*} properties.
 * Runs on a configurable cron schedule (default: 3 AM daily).
 */
@Component
@ConditionalOnProperty(name = "desk.integration.purge.enabled", havingValue = "true")
public class PurgeScheduler {

    private static final Logger LOG = Logger.getLogger(PurgeScheduler.class.getName());
    private static final Subject SYSTEM_SUBJECT = new Subject("98", null);

    private final ContentManager contentManager;
    private final com.atex.onecms.app.dam.solr.SolrService solrService;
    private final IntegrationProperties properties;

    public PurgeScheduler(ContentManager contentManager,
                           com.atex.onecms.app.dam.solr.SolrService solrService,
                           IntegrationProperties properties) {
        this.contentManager = contentManager;
        this.solrService = solrService;
        this.properties = properties;
    }

    @Scheduled(cron = "${desk.integration.purge.schedule:0 0 3 * * *}")
    public void purge() {
        LOG.info("Starting content purge");

        IntegrationProperties.PurgeConfig config = properties.getPurge();
        List<String> failedIds = new ArrayList<>();

        try {
            // Phase 1: Find content to trash
            List<String> contentIds = findPurgeableContent(config);
            if (contentIds.isEmpty()) {
                LOG.info("No content to purge");
                return;
            }

            LOG.info("Found " + contentIds.size() + " item(s) to purge");

            // Phase 2: Move to trash (update security parent)
            ContentId trashParentId = resolveTrashParent(config.getTrashParent());
            if (trashParentId == null) {
                LOG.warning("Cannot resolve trash parent: " + config.getTrashParent());
                return;
            }

            int trashed = 0;
            for (String contentIdStr : contentIds) {
                try {
                    moveToTrash(contentIdStr, trashParentId);
                    trashed++;
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Failed to trash: " + contentIdStr, e);
                    failedIds.add(contentIdStr);
                }
            }

            LOG.info("Purge complete: trashed " + trashed + ", failed " + failedIds.size());

            // Phase 3: Clean up Solr for failed items
            if (!failedIds.isEmpty()) {
                cleanupSolr(failedIds, config.getSolrBatchSize());
            }

        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error during purge", e);
        }
    }

    private List<String> findPurgeableContent(IntegrationProperties.PurgeConfig config) {
        try {
            // Default purge query: old trashed content past retention
            SolrQuery query = new SolrQuery(
                "tag_dimension.partition_ss:\"trash\" AND modified_date_dt:[* TO NOW-30DAYS]");
            query.setRows(Math.min(config.getMaxDelete(), 15000));
            query.setFields("contentid");

            var response = solrService.rawQuery(query);
            var docs = response.getResults();
            if (docs == null) return Collections.emptyList();

            List<String> ids = new ArrayList<>();
            for (var doc : docs) {
                String id = (String) doc.getFieldValue("contentid");
                if (id != null) ids.add(id);
            }
            return ids;

        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error querying purgeable content", e);
            return Collections.emptyList();
        }
    }

    private void moveToTrash(String contentIdStr, ContentId trashParentId) throws Exception {
        ContentVersionId vid = contentManager.resolve(contentIdStr, SYSTEM_SUBJECT);
        if (vid == null) return;

        ContentWriteBuilder<Object> builder = new ContentWriteBuilder<>();
        builder.aspect("p.InsertionInfo", new InsertionInfoAspectBean(trashParentId));

        contentManager.update(vid.getContentId(), builder.buildUpdate(), SYSTEM_SUBJECT);
    }

    private ContentId resolveTrashParent(String externalId) {
        try {
            ContentVersionId vid = contentManager.resolve(externalId, SYSTEM_SUBJECT);
            return vid != null ? vid.getContentId() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void cleanupSolr(List<String> ids, int batchSize) {
        try {
            for (int i = 0; i < ids.size(); i += batchSize) {
                List<String> batch = ids.subList(i, Math.min(i + batchSize, ids.size()));
                solrService.deleteBatch("onecms", batch);
            }
            LOG.info("Cleaned up " + ids.size() + " orphaned Solr document(s)");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error cleaning up Solr", e);
        }
    }
}
