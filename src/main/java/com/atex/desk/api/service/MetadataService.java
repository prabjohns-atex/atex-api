package com.atex.desk.api.service;

import com.atex.onecms.app.dam.solr.SolrService;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.IdUtil;
import com.atex.onecms.content.Subject;
import com.polopoly.metadata.Dimension;
import com.polopoly.metadata.Entity;
import com.polopoly.metadata.Metadata;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service for metadata/taxonomy operations.
 * Provides taxonomy structure browsing, autocomplete via Solr facets,
 * and content-backed entity resolution.
 */
@Service
public class MetadataService {

    private static final Logger LOG = Logger.getLogger(MetadataService.class.getName());
    private static final Subject SYSTEM_SUBJECT = Subject.of("98");

    private final ContentManager contentManager;
    private final SolrService solrService;

    public MetadataService(ContentManager contentManager, @Nullable SolrService solrService) {
        this.contentManager = contentManager;
        this.solrService = solrService;
    }

    /**
     * Get taxonomy/dimension structure by ID.
     * Loads the taxonomy content and returns its dimension/entity structure.
     *
     * The ID can be:
     * - A dimension external ID (e.g., "department.categorydimension.subject")
     * - A content ID for taxonomy content
     * - A legacy categorization ID (e.g., "p.StandardCategorization")
     *
     * In OneCMS/desk-api, taxonomy structure is stored as content with the
     * taxonomy dimension definitions. This implementation queries Solr for
     * all tags in the requested dimension to build the entity tree.
     */
    public Object getStructure(String metadataObjectId, int depth) {
        // Build a dimension by querying Solr for all tags in this dimension
        if (solrService != null) {
            try {
                Dimension dimension = buildDimensionFromSolr(metadataObjectId, depth);
                if (dimension != null) {
                    return dimension;
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Error building dimension from Solr for " + metadataObjectId, e);
            }
        }

        // Try loading as content
        try {
            return loadStructureFromContent(metadataObjectId);
        } catch (Exception e) {
            LOG.log(Level.FINE, "Could not load structure from content for " + metadataObjectId, e);
        }

        return null;
    }

    /**
     * Autocomplete entities within a dimension using Solr faceted search.
     * Returns a Dimension object with matching entities.
     */
    public Dimension complete(String dimensionId, String entityPrefix, String language) throws Exception {
        if (solrService == null) {
            return new Dimension(dimensionId, dimensionId, false);
        }
        String fieldName = "tags_" + dimensionId;
        String autoCompleteFieldName = "tags_autocomplete_" + dimensionId;

        // Build Solr facet query for autocomplete
        String queryString = "+" + autoCompleteFieldName + ":("
            + ClientUtils.escapeQueryChars(entityPrefix.trim()) + "*)";

        SolrQuery solrQuery = new SolrQuery(queryString)
            .addFacetField(fieldName)
            .setRows(0)
            .setFacetLimit(100)
            .setFacetMinCount(1);

        QueryResponse response = solrService.rawQuery(solrQuery);

        List<Entity> entities = new ArrayList<>();
        FacetField facetField = response.getFacetField(fieldName);
        if (facetField != null) {
            for (FacetField.Count count : facetField.getValues()) {
                String name = count.getName();
                if (name != null && !name.isEmpty()) {
                    Entity entity = parseEntityPath(name);
                    if (entity != null) {
                        entities.add(entity);
                    }
                }
            }
        }

        return new Dimension(dimensionId, dimensionId, false, entities);
    }

    /**
     * Annotate text to suggest matching taxonomy entities.
     * Simple implementation: searches for tags whose names appear in the text.
     */
    public Metadata annotate(String taxonomyId, String text) {
        Metadata result = new Metadata();

        if (text == null || text.isBlank() || solrService == null) {
            return result;
        }

        try {
            // Search for tags whose names match words in the text
            String escapedText = ClientUtils.escapeQueryChars(text.toLowerCase());
            // Limit to first 200 chars for the query to avoid Solr query length limits
            if (escapedText.length() > 200) {
                escapedText = escapedText.substring(0, 200);
            }

            SolrQuery query = new SolrQuery(
                "+inputTemplate:atex.onecms.metadata.Tag"
                + " +name_atex_desk_ss:(" + escapedText + ")"
            );
            query.setRows(50);
            query.setFields("id", "name_atex_desk_ss", "tags_autocomplete_*");

            QueryResponse response = solrService.rawQuery(query);

            // Group results by dimension
            if (response.getResults() != null) {
                for (var doc : response.getResults()) {
                    String name = (String) doc.getFirstValue("name_atex_desk_ss");
                    if (name != null && textContainsWord(text, name)) {
                        // Found a matching tag — add to result
                        // For now, create a simple entity
                        Entity entity = new Entity(
                            doc.getFirstValue("id") != null ? doc.getFirstValue("id").toString() : null,
                            name
                        );
                        // Use a generic dimension since we don't know which dimension this tag belongs to
                        Dimension dim = result.getDimensionById(taxonomyId);
                        if (dim == null) {
                            dim = new Dimension(taxonomyId, taxonomyId, false);
                            result.addDimension(dim);
                        }
                        dim.addEntities(entity);
                    }
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error annotating text for taxonomy " + taxonomyId, e);
        }

        return result;
    }

    /**
     * Resolve content-backed entities.
     * For each dimension/entity in the input metadata, looks up the full entity content
     * and enriches it with content IDs and any additional data.
     */
    public Metadata resolveContentBackedEntities(Metadata metadata) {
        if (metadata == null || metadata.getDimensions() == null) {
            return metadata;
        }

        Metadata resolved = new Metadata();
        for (Dimension dim : metadata.getDimensions()) {
            Dimension resolvedDim = new Dimension(dim.getId(), dim.getName(), dim.isEnumerable());
            if (dim.getEntities() != null) {
                for (Entity entity : dim.getEntities()) {
                    Entity resolvedEntity = resolveEntity(dim.getId(), entity);
                    resolvedDim.addEntities(resolvedEntity != null ? resolvedEntity : entity);
                }
            }
            resolved.addDimension(resolvedDim);
        }

        return resolved;
    }

    // --- Private helpers ---

    /**
     * Build a Dimension by querying Solr for all tags in the given dimension.
     */
    private Dimension buildDimensionFromSolr(String dimensionId, int depth) throws Exception {
        SolrQuery query = new SolrQuery(
            "+inputTemplate:atex.onecms.metadata.Tag"
            + " +tags_autocomplete_" + ClientUtils.escapeQueryChars(dimensionId) + ":*"
        );
        query.setRows(1000);
        query.setFields("id", "name_atex_desk_ss");
        query.setSort("name_atex_desk_ss", SolrQuery.ORDER.asc);

        QueryResponse response = solrService.rawQuery(query);

        if (response.getResults() == null || response.getResults().isEmpty()) {
            return null;
        }

        Dimension dimension = new Dimension(dimensionId, dimensionId, false);
        for (var doc : response.getResults()) {
            String name = (String) doc.getFirstValue("name_atex_desk_ss");
            String id = doc.getFirstValue("id") != null ? doc.getFirstValue("id").toString() : null;
            if (name != null) {
                dimension.addEntities(new Entity(id, name));
            }
        }

        return dimension;
    }

    /**
     * Try to load taxonomy structure from content by ID.
     */
    private Object loadStructureFromContent(String metadataObjectId) {
        // Try as external ID first
        ContentVersionId vid = contentManager.resolve(metadataObjectId, SYSTEM_SUBJECT);
        if (vid == null) {
            // Try as content ID
            try {
                ContentId contentId = IdUtil.fromString(metadataObjectId);
                vid = contentManager.resolve(contentId, SYSTEM_SUBJECT);
            } catch (Exception e) {
                // Not a valid content ID
            }
        }

        if (vid == null) {
            return null;
        }

        ContentResult<Object> cr = contentManager.get(vid, null, Object.class,
            Collections.emptyMap(), SYSTEM_SUBJECT);
        if (cr.getStatus().isSuccess()) {
            return cr.getContent().getContentData();
        }
        return null;
    }

    /**
     * Parse a Solr facet entity path like "Sport/Cricket/Bowling" into nested Entity objects.
     */
    private Entity parseEntityPath(String path) {
        if (path == null || path.isEmpty()) return null;

        String[] parts = path.split("/");
        Entity root = new Entity(parts[0], parts[0]);
        Entity current = root;
        for (int i = 1; i < parts.length; i++) {
            Entity child = new Entity(parts[i], parts[i]);
            current.addEntity(child);
            current = child;
        }
        return root;
    }

    /**
     * Resolve a single entity by looking up its content in Solr.
     */
    private Entity resolveEntity(String dimensionId, Entity entity) {
        if (entity == null || entity.getId() == null) return entity;

        try {
            // Try to find by external ID (tagId-{id})
            String externalId = "tagId-" + entity.getId();
            ContentVersionId vid = contentManager.resolve(externalId, SYSTEM_SUBJECT);
            if (vid != null) {
                ContentResult<Object> cr = contentManager.get(vid, null, Object.class,
                    Collections.emptyMap(), SYSTEM_SUBJECT);
                if (cr.getStatus().isSuccess()) {
                    // Enrich entity with content data
                    Entity resolved = new Entity(entity.getId(), entity.getName());
                    resolved.setEntities(entity.getEntities());
                    return resolved;
                }
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "Could not resolve entity " + entity.getId(), e);
        }

        return entity;
    }

    private boolean textContainsWord(String text, String word) {
        if (text == null || word == null) return false;
        String lowerText = text.toLowerCase();
        String lowerWord = word.toLowerCase();
        int idx = lowerText.indexOf(lowerWord);
        if (idx < 0) return false;
        // Check word boundaries
        boolean startOk = idx == 0 || !Character.isLetterOrDigit(lowerText.charAt(idx - 1));
        boolean endOk = idx + lowerWord.length() >= lowerText.length()
            || !Character.isLetterOrDigit(lowerText.charAt(idx + lowerWord.length()));
        return startOk && endOk;
    }
}
