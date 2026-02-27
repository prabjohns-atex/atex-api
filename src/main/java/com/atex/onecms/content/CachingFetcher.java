package com.atex.onecms.content;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple per-request cache wrapper around ContentManager.
 * Avoids redundant lookups when multiple hooks in a chain
 * need the same content.
 */
public class CachingFetcher {

    private static final Logger LOG = Logger.getLogger(CachingFetcher.class.getName());

    private final ContentManager contentManager;
    private final Subject subject;
    private final Map<String, ContentResult<?>> resultCache = new HashMap<>();
    private final Map<String, ContentVersionId> resolveCache = new HashMap<>();

    private CachingFetcher(ContentManager contentManager, Subject subject) {
        this.contentManager = contentManager;
        this.subject = subject;
    }

    public static CachingFetcher create(ContentManager contentManager, Subject subject) {
        return new CachingFetcher(contentManager, subject);
    }

    @SuppressWarnings("unchecked")
    public <T> ContentResult<T> get(ContentVersionId versionId, Class<T> dataClass, Subject subj) {
        if (versionId == null) return null;
        String key = "v:" + IdUtil.toVersionedIdString(versionId);
        ContentResult<?> cached = resultCache.get(key);
        if (cached != null) {
            return (ContentResult<T>) cached;
        }
        try {
            ContentResult<T> result = contentManager.get(versionId, dataClass, subj);
            resultCache.put(key, result);
            return result;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "CachingFetcher.get failed for " + versionId, e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public <T> ContentResult<T> get(ContentId contentId, Class<T> dataClass, Subject subj) {
        if (contentId == null) return null;
        ContentVersionId vid = resolve(contentId, subj);
        if (vid == null) return null;
        return get(vid, dataClass, subj);
    }

    public ContentVersionId resolve(ContentId contentId, Subject subj) {
        if (contentId == null) return null;
        String key = "r:" + IdUtil.toIdString(contentId);
        ContentVersionId cached = resolveCache.get(key);
        if (cached != null) return cached;
        try {
            ContentVersionId vid = contentManager.resolve(contentId, subj);
            if (vid != null) {
                resolveCache.put(key, vid);
            }
            return vid;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "CachingFetcher.resolve failed for " + contentId, e);
            return null;
        }
    }

    public ContentVersionId resolve(String externalId, Subject subj) {
        if (externalId == null) return null;
        String key = "e:" + externalId;
        ContentVersionId cached = resolveCache.get(key);
        if (cached != null) return cached;
        try {
            ContentVersionId vid = contentManager.resolve(externalId, subj);
            if (vid != null) {
                resolveCache.put(key, vid);
            }
            return vid;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "CachingFetcher.resolve failed for externalId " + externalId, e);
            return null;
        }
    }

    public ContentManager getContentManager() {
        return contentManager;
    }

    public Subject getSubject() {
        return subject;
    }
}
