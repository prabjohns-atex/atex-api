package com.atex.desk.api.indexing;

import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.IdUtil;
import com.atex.onecms.app.dam.solr.SolrService;
import com.google.gson.JsonObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Post-store indexer that converts content to a Solr document
 * via DamIndexComposer and pushes it to Solr.
 *
 * <p>Receives the already-fetched ContentResult from LocalContentManager
 * to avoid a circular dependency (LocalContentManager → ContentIndexer → ContentManager).
 */
@Component
public class ContentIndexer {

    private static final Logger LOG = Logger.getLogger(ContentIndexer.class.getName());

    private final SolrService solrService;
    private final DamIndexComposer composer;
    private final boolean enabled;
    private final String collection;

    public ContentIndexer(@Nullable SolrService solrService,
                          DamIndexComposer composer,
                          @Value("${desk.indexing.enabled:true}") boolean enabled,
                          @Value("${desk.solr-core:onecms}") String collection) {
        this.solrService = solrService;
        this.composer = composer;
        this.enabled = enabled;
        this.collection = collection;
    }

    /**
     * Index a content result in Solr. Called by LocalContentManager after create/update
     * with the already-fetched result — no need to re-fetch from ContentManager.
     */
    public void index(ContentResult<Object> contentResult, ContentVersionId versionId) {
        if (!enabled || solrService == null) return;

        try {
            if (contentResult == null || !contentResult.getStatus().isSuccess()
                    || contentResult.getContent() == null) {
                LOG.fine(() -> "Skipping indexing for " + versionId + ": content not found or unsuccessful");
                return;
            }

            JsonObject solrDoc = composer.compose(contentResult, versionId);
            solrService.index(collection, solrDoc);
            LOG.fine(() -> "Indexed content: " + IdUtil.toVersionedIdString(versionId));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to index content: " + versionId, e);
        }
    }

    /**
     * Remove a content ID from the Solr index (soft delete).
     */
    public void delete(ContentId contentId) {
        if (!enabled || solrService == null) return;

        try {
            String id = IdUtil.toIdString(contentId);
            solrService.delete(collection, id);
            LOG.fine(() -> "Deleted from index: " + id);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to delete from index: " + contentId, e);
        }
    }
}
