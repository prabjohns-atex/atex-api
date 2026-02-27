package com.atex.onecms.app.dam.cfg;

import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.Subject;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BeanMapperConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(BeanMapperConfiguration.class);
    private static final String CONFIG_EXTID = "com.atex.onecms.beanmapper.Configuration";
    private static final Gson GSON = new Gson();

    private boolean enabled = true;
    private Map<String, Object> handlers = new HashMap<>();
    private List<String> excludeAspects = new ArrayList<>();
    private List<String> excludeDimensions = new ArrayList<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }
    public Map<String, Object> getHandlers() { return handlers; }
    public void setHandlers(Map<String, Object> v) { this.handlers = v; }
    public List<String> getExcludeAspects() { return excludeAspects; }
    public void setExcludeAspects(List<String> v) { this.excludeAspects = v; }
    public List<String> getExcludeDimensions() { return excludeDimensions; }
    public void setExcludeDimensions(List<String> v) { this.excludeDimensions = v; }

    @SuppressWarnings("unchecked")
    public static BeanMapperConfiguration fetch(ContentManager cm, Subject subject) {
        BeanMapperConfiguration config = new BeanMapperConfiguration();
        try {
            ContentVersionId vid = cm.resolve(CONFIG_EXTID, subject);
            if (vid == null) {
                LOG.debug("BeanMapper configuration not found: {}", CONFIG_EXTID);
                return config;
            }
            ContentResult<Object> cr = cm.get(vid, Object.class, subject);
            if (cr == null || !cr.getStatus().isSuccess() || cr.getContent() == null) {
                return config;
            }
            Object data = cr.getContent().getContentData();
            if (data instanceof Map<?, ?> map) {
                String json = GSON.toJson(map);
                config = GSON.fromJson(json, BeanMapperConfiguration.class);
                if (config == null) {
                    config = new BeanMapperConfiguration();
                }
            }
        } catch (Exception e) {
            LOG.warn("Error loading BeanMapper configuration: {}", e.getMessage());
        }
        return config;
    }
}
