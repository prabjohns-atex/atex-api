package com.atex.desk.api.search;

import com.atex.desk.api.config.DeskProperties;
import com.atex.onecms.app.dam.solr.SolrService;
import com.atex.onecms.content.Subject;
import com.atex.onecms.search.SearchClient;
import com.atex.onecms.search.SearchOptions;
import com.atex.onecms.search.SearchResponse;
import com.atex.onecms.ws.search.SolrQueryDecorator;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Spring component implementing SearchClient, wrapping SolrService
 * for executing Solr queries with permission filtering and working-sites decoration.
 */
@Component
public class LocalSearchClient implements SearchClient {

    private static final Logger LOG = Logger.getLogger(LocalSearchClient.class.getName());

    private final SolrService solrService;
    private final DeskProperties deskProperties;
    private final ConcurrentHashMap<String, SolrService> coreCache = new ConcurrentHashMap<>();

    public LocalSearchClient(@Nullable SolrService solrService, DeskProperties deskProperties) {
        this.solrService = solrService;
        this.deskProperties = deskProperties;
    }

    @Override
    public SearchResponse query(final String core, final SolrQuery query,
                                final Subject subject, final SearchOptions options) {
        if (solrService == null) {
            return new LocalSearchResponse(503, "Solr is not configured", core);
        }

        try {
            // Map core name to actual Solr core
            String targetCore = mapCoreName(core);
            SolrService targetService = getServiceForCore(targetCore);

            SolrQuery decorated = query;

            // Apply working-sites filter if requested
            if (options != null && options.isFilterWorkingSites()) {
                // Working sites filtering is ready for when user working sites are in DB
                // Currently a no-op with empty list
                decorated = new SolrQueryDecorator(decorated)
                    .decorateWithWorkingSites(java.util.List.of())
                    .getSolrQuery();
            }

            // Apply permission filter if requested
            if (options != null && options.getPermission() != SearchOptions.ACCESS_PERMISSION.OFF
                    && subject != null && subject.getPrincipalId() != null) {
                String principalId = subject.getPrincipalId();
                switch (options.getPermission()) {
                    case READ ->
                        decorated.addFilterQuery("content_readers_ss:\"" + principalId + "\" OR content_readers_ss:\"*\"");
                    case WRITE ->
                        decorated.addFilterQuery("content_writers_ss:\"" + principalId + "\" OR content_writers_ss:\"*\"");
                    case OWNER ->
                        decorated.addFilterQuery("content_owners_ss:\"" + principalId + "\" OR content_owners_ss:\"*\"");
                    default -> { }
                }
            }

            QueryResponse response = targetService.rawQuery(decorated);
            return new LocalSearchResponse(response, core);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Search query failed for core " + core, e);
            return new LocalSearchResponse(500, "Search query failed: " + e.getMessage(), core);
        }
    }

    private String mapCoreName(final String core) {
        if (core == null || core.isEmpty() || CORE_ONECMS.equals(core)) {
            return deskProperties.getSolrCore();
        }
        if (CORE_LATEST.equals(core)) {
            return deskProperties.getSolrLatestCore();
        }
        return core;
    }

    private SolrService getServiceForCore(final String coreName) {
        return coreCache.computeIfAbsent(coreName, name -> solrService.forCore(name));
    }
}
