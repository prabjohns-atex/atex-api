package com.atex.desk.api.site;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.atex.desk.api.dto.AspectDto;
import com.atex.desk.api.dto.ContentResultDto;
import com.atex.desk.api.service.ContentService;
import com.atex.onecms.app.siteengine.SiteStructureBean;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.IdUtil;
import com.atex.onecms.content.Subject;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * Builds a site structure tree for the atex.onecms.structure variant.
 * Ported from com.atex.gong.site.structure.SiteStructureUtils.
 */
@Service
public class SiteStructureService {

    private static final Logger LOG = Logger.getLogger(SiteStructureService.class.getName());
    private static final Subject SYSTEM_SUBJECT = new Subject("98", null);

    private final ContentService contentService;
    private final ContentManager contentManager;

    public SiteStructureService(ContentService contentService,
                                @Nullable ContentManager contentManager) {
        this.contentService = contentService;
        this.contentManager = contentManager;
    }

    /**
     * Build a site structure tree from a root content.
     *
     * @param vid           the resolved root content version ID
     * @param excludedSites comma-separated external IDs or delegationId:key strings to exclude
     * @return the structure bean tree, or null if content not found
     */
    public SiteStructureBean getStructure(ContentVersionId vid, String excludedSites) {
        List<String> excluded = parseExcludedSites(excludedSites);
        List<ContentId> excludedIds = resolveExcludedSites(excluded);
        return getSiteStructureBean(vid, excludedIds, new ArrayList<>());
    }

    // --- Excluded sites resolution (ported from SiteStructureUtils.resolveExcludedSites) ---

    private List<String> parseExcludedSites(String excludedSites) {
        List<String> result = new ArrayList<>();
        if (excludedSites == null || excludedSites.isBlank()) return result;
        for (String s : excludedSites.split(",")) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return result;
    }

    private List<ContentId> resolveExcludedSites(List<String> excludedSites) {
        List<ContentId> ids = new ArrayList<>();
        for (String id : excludedSites) {
            ContentId contentId = null;
            try {
                contentId = IdUtil.fromString(id);
            } catch (IllegalArgumentException e) {
                // Resolve as external ID
                if (contentManager != null) {
                    ContentVersionId versionId = contentManager.resolve(id, SYSTEM_SUBJECT);
                    if (versionId != null) {
                        contentId = versionId.getContentId();
                    }
                }
            }
            if (contentId != null && !ids.contains(contentId)) {
                ids.add(contentId);
            }
        }
        return ids;
    }

    // --- Main tree builder (ported from SiteStructureUtils.getSiteStructureBean) ---

    @SuppressWarnings("unchecked")
    private SiteStructureBean getSiteStructureBean(ContentVersionId vid,
                                                    List<ContentId> excludedSites,
                                                    List<ContentId> alreadySeen) {
        ContentId cid = vid.getContentId();

        if (alreadySeen.contains(cid)) {
            return null;
        }
        alreadySeen.add(cid);

        if (excludedSites.contains(cid)) {
            return null;
        }

        // Check parent chain for exclusions
        if (!excludedSites.isEmpty()) {
            List<ContentId> parentIds = getParentIds(cid);
            boolean exclude = parentIds.stream().anyMatch(excludedSites::contains);
            if (exclude) {
                return null;
            }
        }

        // Fetch content
        Optional<ContentResultDto> opt = contentService.getContent(
            vid.getDelegationId(), vid.getKey(), vid.getVersion());
        if (opt.isEmpty()) return null;

        ContentResultDto content = opt.get();
        Map<String, AspectDto> aspects = content.getAspects();
        if (aspects == null) return null;

        AspectDto contentDataAspect = aspects.get("contentData");
        Map<String, Object> contentData = contentDataAspect != null ? contentDataAspect.getData() : null;
        if (contentData == null) return null;

        // Build the SiteStructureBean (equivalent to BeanUtils.copyProperties + setId)
        SiteStructureBean result = new SiteStructureBean();
        result.setId(cid);
        result.setName(contentData.get("name") instanceof String s ? s : null);
        result.setPathSegment(contentData.get("pathSegment") instanceof String s ? s : null);

        // Extract externalId from meta.aliases (desk-api stores aliases in meta, not as aspect)
        if (content.getMeta() != null && content.getMeta().getAliases() != null) {
            String extId = content.getMeta().getAliases().get("externalId");
            if (extId != null) result.setExternalId(extId);
        }

        // Recursively process subPages
        Object subPagesObj = contentData.get("subPages");
        if (subPagesObj instanceof List<?> subPagesList && !subPagesList.isEmpty()) {
            List<SiteStructureBean> children = subPagesList.stream()
                .map(this::resolveSubPageRef)
                .filter(Objects::nonNull)
                .map(childVid -> getSiteStructureBean(childVid, excludedSites, alreadySeen))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            result.setChildren(children);
        }

        return result;
    }

    // --- Parent chain walker (ported from SiteStructureUtils.getParentIds) ---

    @SuppressWarnings("unchecked")
    private List<ContentId> getParentIds(ContentId contentId) {
        // Resolve site root to stop the walk
        ContentId siteRootId = null;
        if (contentManager != null) {
            ContentVersionId siteRootVid = contentManager.resolve("p.siteengine.Sites.d", SYSTEM_SUBJECT);
            if (siteRootVid != null) {
                siteRootId = siteRootVid.getContentId();
            }
        }

        List<ContentId> parents = new ArrayList<>();
        parents.add(contentId);
        ContentId current = contentId;
        int depth = 0;

        while (depth++ < 50) {
            ContentVersionId vid = resolveContentId(current);
            if (vid == null) break;

            Optional<ContentResultDto> opt = contentService.getContent(
                vid.getDelegationId(), vid.getKey(), vid.getVersion());
            if (opt.isEmpty()) break;

            Map<String, AspectDto> aspects = opt.get().getAspects();
            if (aspects == null) break;

            AspectDto insInfoAspect = aspects.get("p.InsertionInfo");
            if (insInfoAspect == null || insInfoAspect.getData() == null) break;

            Map<String, Object> insInfo = insInfoAspect.getData();

            // Try insertParentId then securityParentContentId (matches original order)
            ContentId parentId = extractContentIdField(insInfo, "insertParentId");
            if (parentId == null) {
                parentId = extractContentIdField(insInfo, "securityParentContentId");
            }
            if (parentId == null) break;
            if (parentId.equals(siteRootId)) break;

            parents.add(0, parentId);
            current = parentId;
        }
        return parents;
    }

    // --- ID resolution helpers ---

    private ContentVersionId resolveContentId(ContentId cid) {
        if (contentManager != null) {
            return contentManager.resolve(cid, SYSTEM_SUBJECT);
        }
        Optional<String> resolved = contentService.resolve(cid.getDelegationId(), cid.getKey());
        return resolved.map(IdUtil::fromVersionedString).orElse(null);
    }

    /**
     * Resolve a subPage reference to a ContentVersionId.
     * SubPages may be stored as string IDs, or as maps with delegationId/key fields.
     */
    @SuppressWarnings("unchecked")
    private ContentVersionId resolveSubPageRef(Object ref) {
        ContentId cid = null;

        if (ref instanceof String s) {
            try {
                cid = IdUtil.fromString(s);
            } catch (IllegalArgumentException e) {
                // Try as external ID
                if (contentManager != null) {
                    ContentVersionId vid = contentManager.resolve(s, SYSTEM_SUBJECT);
                    if (vid != null) return vid;
                }
                return null;
            }
        } else if (ref instanceof Map<?,?> map) {
            Object delegationId = map.get("delegationId");
            Object key = map.get("key");
            if (delegationId != null && key != null) {
                cid = new ContentId(delegationId.toString(), key.toString());
            }
        }

        if (cid == null) return null;
        return resolveContentId(cid);
    }

    /**
     * Extract a ContentId from an InsertionInfo field.
     * Field value may be a string "delegationId:key" or a map {delegationId, key}.
     */
    @SuppressWarnings("unchecked")
    private ContentId extractContentIdField(Map<String, Object> data, String fieldName) {
        Object value = data.get(fieldName);
        if (value instanceof String s) {
            try { return IdUtil.fromString(s); }
            catch (IllegalArgumentException ignored) {}
        } else if (value instanceof Map<?,?> map) {
            Object d = map.get("delegationId");
            Object k = map.get("key");
            if (d != null && k != null) return new ContentId(d.toString(), k.toString());
        }
        return null;
    }
}
