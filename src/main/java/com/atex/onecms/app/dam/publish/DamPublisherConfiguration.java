package com.atex.onecms.app.dam.publish;

import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.Subject;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DamPublisherConfiguration extends DamStatusConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(DamPublisherConfiguration.class);
    public static final String BEAN_PUBLISHER_CONFIG_EXTID = "com.atex.onecms.dam.beanPublisher.Configuration";
    private static final Gson GSON = new Gson();

    private boolean preferInsertParentId = true;
    private boolean useUrlForSecurityParentIdInLegacyBean;
    private boolean ignoreAuthenticationFailed = true;
    private int maxImageWidth = 900;
    private double imageQuality = 0.75;
    private String defaultSecurityParentId = "PolopolyPost.d";
    private boolean useDistributedLock;
    private Map<String, List<String>> nonPublishableStatus = new HashMap<>();
    private Map<String, List<String>> nonPublishableWebStatus = new HashMap<>();
    private List<String> privateContentAsEmpty = new ArrayList<>();
    private List<String> checkContentDataClass = new ArrayList<>();

    public boolean isPreferInsertParentId() { return preferInsertParentId; }
    public boolean isUseUrlForSecurityParentIdInLegacyBean() { return useUrlForSecurityParentIdInLegacyBean; }
    public boolean isIgnoreAuthenticationFailed() { return ignoreAuthenticationFailed; }
    public int getMaxImageWidth() { return maxImageWidth; }
    public double getImageQuality() { return imageQuality; }
    public String getDefaultSecurityParentId() { return defaultSecurityParentId; }
    public boolean isUseDistributedLock() { return useDistributedLock; }
    public Map<String, List<String>> getNonPublishableStatus() { return nonPublishableStatus; }
    public Map<String, List<String>> getNonPublishableWebStatus() { return nonPublishableWebStatus; }
    public List<String> getPrivateContentAsEmpty() { return privateContentAsEmpty; }
    public List<String> getCheckContentDataClass() { return checkContentDataClass; }

    public static DamPublisherConfiguration fetch(ContentManager cm, Subject subject) {
        return fetch(cm, BEAN_PUBLISHER_CONFIG_EXTID, subject);
    }

    @SuppressWarnings("unchecked")
    public static DamPublisherConfiguration fetch(ContentManager cm, String configId, Subject subject) {
        DamPublisherConfiguration config = new DamPublisherConfiguration();
        try {
            ContentVersionId vid = cm.resolve(configId, subject);
            if (vid == null) {
                LOG.debug("Publisher configuration not found: {}", configId);
                return config;
            }
            ContentResult<Object> cr = cm.get(vid, Object.class, subject);
            if (cr == null || !cr.getStatus().isSuccess() || cr.getContent() == null) {
                return config;
            }
            Object data = cr.getContent().getContentData();
            if (data instanceof Map<?, ?> map) {
                String json = GSON.toJson(map);
                config = parse(json);
            }
        } catch (Exception e) {
            LOG.warn("Error loading publisher configuration: {}", e.getMessage());
        }
        return config;
    }

    public static DamPublisherConfiguration parse(String json) {
        DamPublisherConfiguration config = new DamPublisherConfiguration();
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            config.parseStatusConfig(root);
            config.preferInsertParentId = getBool(root, "preferInsertParentId", true);
            config.useUrlForSecurityParentIdInLegacyBean = getBool(root, "useUrlForSecurityParentIdInLegacyBean", false);
            config.ignoreAuthenticationFailed = getBool(root, "ignoreAuthenticationFailed", true);
            config.defaultSecurityParentId = getStr(root, "defaultSecurityParentId");
            if (config.defaultSecurityParentId == null) config.defaultSecurityParentId = "PolopolyPost.d";
            config.useDistributedLock = getBool(root, "useDistributedLock", false);

            // Allow system property overrides
            String sysPropWidth = System.getProperty("desk.config.webImageSize");
            config.maxImageWidth = sysPropWidth != null ? Integer.parseInt(sysPropWidth) :
                getInt(root, "maxImageWidth", 900);

            String sysPropQuality = System.getProperty("desk.config.webImageQuality");
            config.imageQuality = sysPropQuality != null ? Double.parseDouble(sysPropQuality) :
                getDouble(root, "imageQuality", 0.75);
        } catch (Exception e) {
            LOG.warn("Error parsing publisher configuration: {}", e.getMessage());
        }
        return config;
    }
}
