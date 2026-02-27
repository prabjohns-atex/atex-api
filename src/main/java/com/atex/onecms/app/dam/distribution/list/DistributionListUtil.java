package com.atex.onecms.app.dam.distribution.list;

import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DistributionListUtil {
    private static final Logger LOG = LoggerFactory.getLogger(DistributionListUtil.class);
    private static final String CONFIG_EXTERNAL_ID = "dam.distributionlist.conf";
    private static final Subject SYSTEM_SUBJECT = new Subject("98", null);

    private final ContentManager contentManager;
    private final Map<String, List<String>> cache = new ConcurrentHashMap<>();
    private volatile long cacheTime;

    public DistributionListUtil(ContentManager contentManager) {
        this.contentManager = contentManager;
    }

    @SuppressWarnings("unchecked")
    public List<String> expandDistributionLists(List<String> originalEmails) {
        if (originalEmails == null || originalEmails.isEmpty()) {
            return originalEmails;
        }

        Set<String> result = new LinkedHashSet<>();
        Map<String, List<String>> lists = getDistributionLists();

        for (String entry : originalEmails) {
            if (entry == null) continue;
            String trimmed = entry.trim().toLowerCase();
            if (trimmed.contains("@")) {
                // Direct email address
                result.add(trimmed);
            } else {
                // Distribution list name
                List<String> expanded = lists.get(trimmed);
                if (expanded != null) {
                    for (String email : expanded) {
                        result.add(email.trim().toLowerCase());
                    }
                } else {
                    LOG.debug("Unknown distribution list: {}", trimmed);
                }
            }
        }
        return new ArrayList<>(result);
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<String>> getDistributionLists() {
        long now = System.currentTimeMillis();
        // Cache for 1 minute
        if (!cache.isEmpty() && (now - cacheTime) < 60_000) {
            return cache;
        }
        cache.clear();
        try {
            ContentVersionId vid = contentManager.resolve(CONFIG_EXTERNAL_ID, SYSTEM_SUBJECT);
            if (vid == null) return cache;
            ContentResult<Object> cr = contentManager.get(vid, Object.class, SYSTEM_SUBJECT);
            if (cr == null || !cr.getStatus().isSuccess() || cr.getContent() == null) return cache;

            Object data = cr.getContent().getContentData();
            if (data instanceof Map<?, ?> map) {
                Object listsObj = map.get("lists");
                if (listsObj instanceof List<?> listEntries) {
                    for (Object listEntry : listEntries) {
                        if (listEntry instanceof Map<?, ?> entryMap) {
                            String name = entryMap.get("name") != null ? entryMap.get("name").toString() : null;
                            String type = entryMap.get("type") != null ? entryMap.get("type").toString() : null;
                            if (name != null && "email".equals(type)) {
                                Object emails = entryMap.get("emails");
                                if (emails instanceof List<?> emailList) {
                                    List<String> emailStrings = new ArrayList<>();
                                    for (Object e : emailList) {
                                        if (e != null) emailStrings.add(e.toString().trim());
                                    }
                                    cache.put(name.toLowerCase(), emailStrings);
                                }
                            }
                        }
                    }
                }
            }
            cacheTime = now;
        } catch (Exception e) {
            LOG.warn("Error loading distribution lists: {}", e.getMessage());
        }
        return cache;
    }
}
