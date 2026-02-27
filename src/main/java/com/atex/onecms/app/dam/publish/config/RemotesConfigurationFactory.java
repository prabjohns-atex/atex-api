package com.atex.onecms.app.dam.publish.config;

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RemotesConfigurationFactory {
    private static final Logger LOG = LoggerFactory.getLogger(RemotesConfigurationFactory.class);
    public static final String REMOTES_CONFIG_EXTERNAL_ID = "com.atex.onecms.dam.remotes.Configuration";
    private static final Gson GSON = new Gson();

    public static RemotesConfiguration fetch(ContentManager cm, Subject subject) {
        RemotesConfiguration config = new RemotesConfiguration();
        try {
            ContentVersionId vid = cm.resolve(REMOTES_CONFIG_EXTERNAL_ID, subject);
            if (vid == null) {
                LOG.debug("Remotes configuration not found: {}", REMOTES_CONFIG_EXTERNAL_ID);
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
            LOG.warn("Error loading remotes configuration: {}", e.getMessage());
        }
        return config;
    }

    public static RemotesConfiguration parse(String json) {
        RemotesConfiguration config = new RemotesConfiguration();
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            // Parse configurations array
            Map<String, RemoteConfigBean> configurations = new HashMap<>();
            if (root.has("configurations")) {
                JsonArray configsArray = root.getAsJsonArray("configurations");
                for (JsonElement elem : configsArray) {
                    JsonObject obj = elem.getAsJsonObject();
                    String id = obj.has("id") ? obj.get("id").getAsString() : null;
                    if (id != null && obj.has("data")) {
                        RemoteConfigBean bean = GSON.fromJson(obj.get("data"), RemoteConfigBean.class);
                        bean.setId(id);
                        configurations.put(id, bean);
                    }
                }
            }
            config.setConfigurations(configurations);

            // Parse publish rules
            List<RemoteConfigRuleBean> publishRules = new ArrayList<>();
            if (root.has("rules")) {
                JsonObject rules = root.getAsJsonObject("rules");
                if (rules.has("publish")) {
                    JsonArray rulesArray = rules.getAsJsonArray("publish");
                    for (JsonElement elem : rulesArray) {
                        RemoteConfigRuleBean rule = GSON.fromJson(elem, RemoteConfigRuleBean.class);
                        publishRules.add(rule);
                    }
                }
            }
            config.setPublishRules(publishRules);

            // Parse defaults
            if (root.has("defaults")) {
                Map<String, String> defaults = new HashMap<>();
                JsonObject defaultsObj = root.getAsJsonObject("defaults");
                for (var entry : defaultsObj.entrySet()) {
                    if (!entry.getValue().isJsonNull()) {
                        defaults.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
                config.setDefaults(defaults);
            }
        } catch (Exception e) {
            LOG.warn("Error parsing remotes configuration: {}", e.getMessage());
        }
        return config;
    }
}
