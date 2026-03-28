package com.atex.onecms.app.mytype.config;

import com.atex.desk.api.config.ConfigurationService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MyTypeConfiguration -- save-rule configuration for mytype content operations.
 * Ported from adm-starterkit MyTypeConfiguration.
 *
 * @author mnova
 */
public class MyTypeConfiguration {

    private static final Logger LOGGER = Logger.getLogger(MyTypeConfiguration.class.getName());

    public static final String EXTERNAL_ID = "mytype.general.configuration";

    private static final Gson GSON = new GsonBuilder().create();

    private final List<SaveRule> saveRules = new ArrayList<>();

    public List<SaveRule> getSaveRules() {
        return saveRules;
    }

    /**
     * Fetch the configuration from ConfigurationService.
     */
    public static MyTypeConfiguration fetch(ConfigurationService configurationService) {
        if (configurationService == null) {
            return new MyTypeConfiguration();
        }
        Optional<Map<String, Object>> configOpt = configurationService.getConfiguration(EXTERNAL_ID);
        if (configOpt.isEmpty()) {
            return new MyTypeConfiguration();
        }
        try {
            String json = GSON.toJson(configOpt.get());
            return parse(json);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to parse MyTypeConfiguration", e);
            return new MyTypeConfiguration();
        }
    }

    /**
     * Parse configuration JSON.
     * Expected format:
     * <pre>
     * {
     *   "endpoint": {
     *     "operations": {
     *       "save": [
     *         {"type": "*", "status": "atex.WFContentStatus:published", "action": "publish"},
     *         {"type": "*", "status": "atex.WebContentStatus:published", "action": "publish"}
     *       ]
     *     }
     *   }
     * }
     * </pre>
     */
    public static MyTypeConfiguration parse(final String json) {
        final MyTypeConfiguration c = new MyTypeConfiguration();
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) return c;

            JsonObject rootObj = root.getAsJsonObject();
            if (!rootObj.has("endpoint")) return c;

            JsonElement endpointEl = rootObj.get("endpoint");
            if (!endpointEl.isJsonObject()) return c;

            JsonObject endpoint = endpointEl.getAsJsonObject();
            if (!endpoint.has("operations")) return c;

            JsonElement operationsEl = endpoint.get("operations");
            if (!operationsEl.isJsonObject()) return c;

            JsonObject operations = operationsEl.getAsJsonObject();
            if (!operations.has("save")) return c;

            JsonElement saveEl = operations.get("save");
            if (!saveEl.isJsonArray()) return c;

            JsonArray save = saveEl.getAsJsonArray();
            for (JsonElement el : save) {
                if (!el.isJsonObject()) continue;
                JsonObject jo = el.getAsJsonObject();
                String type = jo.has("type") ? jo.get("type").getAsString() : "";
                String status = jo.has("status") ? jo.get("status").getAsString() : "";
                String actionStr = jo.has("action") ? jo.get("action").getAsString() : "";
                try {
                    SaveAction action = SaveAction.valueOf(actionStr.toUpperCase());
                    c.saveRules.add(new SaveRule(type, status, action));
                } catch (IllegalArgumentException e) {
                    LOGGER.log(Level.WARNING, "Unknown save action: " + actionStr);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to parse MyTypeConfiguration JSON", e);
        }
        return c;
    }

    public static class SaveRule {
        private final String contentType;
        private final String statusMatch;
        private final SaveAction action;

        public SaveRule(final String contentType,
                        final String statusMatch,
                        final SaveAction action) {
            this.contentType = contentType != null ? contentType : "";
            this.statusMatch = statusMatch != null ? statusMatch : "";
            this.action = action != null ? action : SaveAction.NONE;
        }

        public String getContentType() {
            return contentType;
        }

        public String getStatusMatch() {
            return statusMatch;
        }

        public SaveAction getAction() {
            return action;
        }
    }

    public enum SaveAction {
        NONE,
        PUBLISH,
        UNPUBLISH
    }
}
