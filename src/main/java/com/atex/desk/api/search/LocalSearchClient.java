package com.atex.desk.api.search;

import com.atex.desk.api.config.DeskProperties;
import com.atex.desk.api.entity.AppGroupMember;
import com.atex.desk.api.entity.AppUser;
import com.atex.desk.api.repository.AppGroupMemberRepository;
import com.atex.desk.api.repository.AppUserRepository;
import com.atex.desk.api.service.ObjectCacheService;
import com.atex.onecms.app.dam.solr.SolrService;
import com.atex.onecms.content.Subject;
import com.atex.onecms.search.SearchClient;
import com.atex.onecms.search.SearchOptions;
import com.atex.onecms.search.SearchResponse;
import com.atex.onecms.ws.search.SolrQueryDecorator;
import jakarta.annotation.PostConstruct;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Spring component implementing SearchClient, wrapping SolrService
 * for executing Solr queries with permission filtering and working-sites decoration.
 */
@Component
public class LocalSearchClient implements SearchClient {

    private static final Logger LOG = Logger.getLogger(LocalSearchClient.class.getName());
    private static final String CACHE_PRINCIPALS = "searchPrincipals";

    private final SolrService solrService;
    private final DeskProperties deskProperties;
    private final AppUserRepository appUserRepository;
    private final AppGroupMemberRepository appGroupMemberRepository;
    private final ObjectCacheService cacheService;
    private final ConcurrentHashMap<String, SolrService> coreCache = new ConcurrentHashMap<>();

    public LocalSearchClient(@Nullable SolrService solrService,
                             DeskProperties deskProperties,
                             AppUserRepository appUserRepository,
                             AppGroupMemberRepository appGroupMemberRepository,
                             ObjectCacheService cacheService) {
        this.solrService = solrService;
        this.deskProperties = deskProperties;
        this.appUserRepository = appUserRepository;
        this.appGroupMemberRepository = appGroupMemberRepository;
        this.cacheService = cacheService;
    }

    @PostConstruct
    void initCaches() {
        cacheService.configure(CACHE_PRINCIPALS, 5 * 60 * 1000L, 200);
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

            // Apply permission filter using double-negation pattern (matching reference)
            if (options != null && options.getPermission() != SearchOptions.ACCESS_PERMISSION.OFF
                    && subject != null && subject.getPrincipalId() != null) {
                String permField = getPermissionFieldName(options.getPermission());
                if (permField != null) {
                    List<String> principals = resolvePrincipals(subject.getPrincipalId());
                    String principalExpr = principals.stream()
                            .map(ClientUtils::escapeQueryChars)
                            .collect(Collectors.joining(" OR "));
                    // Double negation: exclude docs that are private AND user is NOT in the permission list
                    // Public docs (no content_private_b or content_private_b:false) pass through
                    String fq = String.format("-(content_private_b:* -content_private_b:false -%s:(%s))",
                            permField, principalExpr);
                    decorated.addFilterQuery(fq);
                }
            }

            QueryResponse response = targetService.rawQuery(decorated);
            return new LocalSearchResponse(response, core);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Search query failed for core " + core, e);
            return new LocalSearchResponse(500, "Search query failed: " + e.getMessage(), core);
        }
    }

    /**
     * Resolve a loginName to the full list of principals (numeric userId, group:X, group:all).
     */
    private List<String> resolvePrincipals(String loginName) {
        String cached = cacheService.get(CACHE_PRINCIPALS, loginName);
        if (cached != null) {
            return List.of(cached.split(","));
        }

        List<String> principals = new ArrayList<>();

        // Look up numeric principalId from loginName
        String numericId = appUserRepository.findByLoginName(loginName)
                .map(AppUser::getPrincipalId)
                .orElse(null);

        if (numericId != null) {
            principals.add(numericId);
            // Look up group memberships by numeric principalId
            List<AppGroupMember> memberships = appGroupMemberRepository.findByPrincipalId(numericId);
            for (AppGroupMember gm : memberships) {
                principals.add("group:" + gm.getGroupId());
            }
        } else {
            // Fallback: use loginName as-is (shouldn't normally happen)
            principals.add(loginName);
        }

        // All users are implicit members of "group:all"
        principals.add("group:all");

        cacheService.put(CACHE_PRINCIPALS, loginName, String.join(",", principals));
        return principals;
    }

    private String getPermissionFieldName(SearchOptions.ACCESS_PERMISSION permission) {
        return switch (permission) {
            case READ -> "content_readers_ss";
            case WRITE -> "content_writers_ss";
            case OWNER -> "content_owners_ss";
            default -> null;
        };
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
