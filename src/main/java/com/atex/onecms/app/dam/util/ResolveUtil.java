package com.atex.onecms.app.dam.util;

import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.IdUtil;
import com.atex.onecms.content.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ResolveUtil {
    private static final Logger LOG = LoggerFactory.getLogger(ResolveUtil.class);
    private static final Subject SYSTEM_SUBJECT = new Subject("98", null);

    private final ContentManager contentManager;
    private final Map<String, String> externalIdCache = new ConcurrentHashMap<>();
    private final Map<String, ContentVersionId> resolveCache = new ConcurrentHashMap<>();

    public ResolveUtil(ContentManager contentManager) {
        this.contentManager = contentManager;
    }

    /**
     * Get the external ID for a content ID.
     * Returns the external ID string, or the ID string representation if no alias found.
     */
    public String getExternalId(ContentId contentId) {
        if (contentId == null) return null;
        String key = IdUtil.toIdString(contentId);
        return externalIdCache.computeIfAbsent(key, k -> {
            try {
                // In our system, external IDs are stored as aliases.
                // For now, return the string representation of the content ID.
                // The alias lookup will be done if the content has a SetAliasOperation.
                ContentVersionId vid = contentManager.resolve(contentId, SYSTEM_SUBJECT);
                if (vid == null) return k;
                ContentResult<Object> cr = contentManager.get(vid, Object.class, SYSTEM_SUBJECT);
                if (cr != null && cr.getStatus().isSuccess() && cr.getContent() != null) {
                    // Check if there's an alias in the aspects
                    for (String aspectName : cr.getContent().getAspectNames()) {
                        if (aspectName.startsWith("alias:")) {
                            return aspectName.substring(6);
                        }
                    }
                }
                return k;
            } catch (Exception e) {
                LOG.debug("Cannot resolve external ID for {}: {}", k, e.getMessage());
                return k;
            }
        });
    }

    /**
     * Resolve an external ID string to a ContentVersionId.
     */
    public ContentVersionId resolveExternalId(String externalId) {
        if (externalId == null || externalId.isEmpty()) return null;
        return resolveCache.computeIfAbsent(externalId, k -> {
            try {
                return contentManager.resolve(externalId, SYSTEM_SUBJECT);
            } catch (Exception e) {
                LOG.debug("Cannot resolve external ID {}: {}", k, e.getMessage());
                return null;
            }
        });
    }

    public void invalidate() {
        externalIdCache.clear();
        resolveCache.clear();
    }
}
