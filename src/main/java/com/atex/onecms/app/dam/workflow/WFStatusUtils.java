package com.atex.onecms.app.dam.workflow;

import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WFStatusUtils {
    private static final Logger LOG = LoggerFactory.getLogger(WFStatusUtils.class);
    private static final String WF_STATUS_LIST_EXTID = "dam.wfstatuslist.d";
    private static final String WEB_STATUS_LIST_EXTID = "dam.webstatuslist.d";
    private static final Subject SYSTEM_SUBJECT = new Subject("98", null);
    private static final long CACHE_TTL_MS = 60_000; // 1 minute

    /** Shared cache across all WFStatusUtils instances (status lists rarely change). */
    private static final ConcurrentHashMap<String, CachedStatusList> STATUS_CACHE = new ConcurrentHashMap<>();

    private record CachedStatusList(List<WFStatusBean> statuses, long expiresAt) {
        boolean isExpired() { return System.currentTimeMillis() > expiresAt; }
    }

    private final ContentManager contentManager;

    public WFStatusUtils(ContentManager contentManager) {
        this.contentManager = contentManager;
    }

    /** Clear cached status lists (e.g., after config change). */
    public static void clearCache() {
        STATUS_CACHE.clear();
    }

    public WFStatusBean getStatusById(String statusId) {
        return findStatus(statusId, getStatusList(WF_STATUS_LIST_EXTID));
    }

    public WFStatusBean getWebStatusById(String statusId) {
        return findStatus(statusId, getStatusList(WEB_STATUS_LIST_EXTID));
    }

    public WFStatusBean getInitialStatus(String type, String inputTemplate) {
        List<WFStatusBean> statuses = getStatusList(WF_STATUS_LIST_EXTID);
        return statuses.isEmpty() ? null : statuses.get(0);
    }

    public WFStatusBean getInitialWebStatus() {
        List<WFStatusBean> statuses = getStatusList(WEB_STATUS_LIST_EXTID);
        return statuses.isEmpty() ? null : statuses.get(0);
    }

    private WFStatusBean findStatus(String statusId, List<WFStatusBean> statuses) {
        if (statusId == null) return null;
        return statuses.stream()
            .filter(s -> statusId.equals(s.getStatusID()))
            .findFirst()
            .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private List<WFStatusBean> getStatusList(String externalId) {
        CachedStatusList cached = STATUS_CACHE.get(externalId);
        if (cached != null && !cached.isExpired()) {
            return cached.statuses();
        }
        try {
            ContentVersionId vid = contentManager.resolve(externalId, SYSTEM_SUBJECT);
            if (vid == null) {
                LOG.debug("Cannot resolve status list: {}", externalId);
                return Collections.emptyList();
            }
            ContentResult<Object> cr = contentManager.get(vid, Object.class, SYSTEM_SUBJECT);
            if (cr == null || !cr.getStatus().isSuccess() || cr.getContent() == null) {
                return Collections.emptyList();
            }
            Object data = cr.getContent().getContentData();
            if (data instanceof Map<?,?> map) {
                Object statusesObj = map.get("statuses");
                if (statusesObj == null) statusesObj = map.get("status");
                if (statusesObj instanceof List<?> list) {
                    List<WFStatusBean> result = list.stream()
                        .filter(Map.class::isInstance)
                        .map(m -> mapToStatusBean((Map<String, Object>) m))
                        .toList();
                    STATUS_CACHE.put(externalId, new CachedStatusList(result,
                        System.currentTimeMillis() + CACHE_TTL_MS));
                    return result;
                }
            }
        } catch (Exception e) {
            LOG.warn("Error loading status list {}: {}", externalId, e.getMessage());
        }
        return Collections.emptyList();
    }

    private WFStatusBean mapToStatusBean(Map<String, Object> map) {
        WFStatusBean bean = new WFStatusBean();
        bean.setStatus(asString(map.get("status")));
        bean.setStatusID(asString(map.get("statusID")));
        bean.setName(asString(map.get("name")));
        bean.setLabel(asString(map.get("label")));
        bean.setColor(asString(map.get("color")));
        return bean;
    }

    private String asString(Object o) {
        return o == null ? null : o.toString();
    }
}
