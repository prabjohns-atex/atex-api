package com.atex.onecms.app.dam.restrict;

import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.Subject;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class RestrictContentConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(RestrictContentConfiguration.class);
    private static final String CONFIG_EXTERNAL_ID = "atex.configuration.desk.restrictContent";
    private static final Gson GSON = new Gson();

    private final Map<String, Configuration> restrictMap = new HashMap<>();
    private final Map<String, Configuration> unRestrictMap = new HashMap<>();

    public static RestrictContentConfiguration fetch(ContentManager cm, Subject subject) {
        RestrictContentConfiguration config = new RestrictContentConfiguration();
        try {
            ContentVersionId vid = cm.resolve(CONFIG_EXTERNAL_ID, subject);
            if (vid == null) {
                LOG.debug("Restrict content configuration not found: {}", CONFIG_EXTERNAL_ID);
                return config;
            }
            ContentResult<Object> cr = cm.get(vid, Object.class, subject);
            if (cr == null || !cr.getStatus().isSuccess() || cr.getContent() == null) {
                return config;
            }
            Object data = cr.getContent().getContentData();
            if (data instanceof Map<?, ?> map) {
                String json = GSON.toJson(map);
                config.parse(json);
            }
        } catch (Exception e) {
            LOG.warn("Error loading restrict content configuration: {}", e.getMessage());
        }
        return config;
    }

    private void parse(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("configurations")) {
                JsonArray configs = root.getAsJsonArray("configurations");
                for (JsonElement elem : configs) {
                    JsonObject obj = elem.getAsJsonObject();
                    String securityParentId = getStr(obj, "securityParentId");
                    if (securityParentId == null) continue;

                    if (obj.has("restrict")) {
                        Configuration restrictCfg = parseConfiguration(obj.getAsJsonObject("restrict"), securityParentId);
                        restrictMap.put(securityParentId, restrictCfg);
                    }
                    if (obj.has("unrestrict")) {
                        Configuration unrestrictCfg = parseConfiguration(obj.getAsJsonObject("unrestrict"), securityParentId);
                        unRestrictMap.put(securityParentId, unrestrictCfg);
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Error parsing restrict content configuration: {}", e.getMessage());
        }
    }

    private Configuration parseConfiguration(JsonObject obj, String defaultParentId) {
        Configuration cfg = new Configuration();
        cfg.setSecurityParentId(getStr(obj, "securityParentId"));
        cfg.setAssociatedSiteId(getStr(obj, "associatedSiteId"));
        cfg.setPrintStatusId(getStr(obj, "printStatusId"));
        cfg.setWebStatusId(getStr(obj, "webStatusId"));
        cfg.setForceChangeInsertionParentId(
            obj.has("forceChangeInsertionParentId") && obj.get("forceChangeInsertionParentId").getAsBoolean());
        return cfg;
    }

    private String getStr(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : null;
    }

    public Optional<Configuration> getRestrict(String securityParentId) {
        return Optional.ofNullable(restrictMap.get(securityParentId));
    }

    public Optional<Configuration> getUnRestrict(String securityParentId) {
        return Optional.ofNullable(unRestrictMap.get(securityParentId));
    }

    public static class Configuration {
        private String securityParentId;
        private String associatedSiteId;
        private String printStatusId;
        private String webStatusId;
        private boolean forceChangeInsertionParentId;

        public String getSecurityParentId() { return securityParentId; }
        public void setSecurityParentId(String v) { this.securityParentId = v; }
        public String getAssociatedSiteId() { return associatedSiteId; }
        public void setAssociatedSiteId(String v) { this.associatedSiteId = v; }
        public String getPrintStatusId() { return printStatusId; }
        public void setPrintStatusId(String v) { this.printStatusId = v; }
        public String getWebStatusId() { return webStatusId; }
        public void setWebStatusId(String v) { this.webStatusId = v; }
        public boolean isForceChangeInsertionParentId() { return forceChangeInsertionParentId; }
        public void setForceChangeInsertionParentId(boolean v) { this.forceChangeInsertionParentId = v; }
    }
}
