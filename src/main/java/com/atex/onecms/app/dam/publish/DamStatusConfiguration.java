package com.atex.onecms.app.dam.publish;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;

public class DamStatusConfiguration {
    private boolean setContentStatusAttributes;
    private boolean setContentStatus;
    private boolean setWebStatus;
    private Map<String, String> statusAttributes = new HashMap<>();
    private Map<String, String> contentStatusByWorkflow = new HashMap<>();
    private Map<String, String> webStatusByWorkflow = new HashMap<>();

    public boolean isSetContentStatusAttributes() { return setContentStatusAttributes; }
    public void setSetContentStatusAttributes(boolean v) { this.setContentStatusAttributes = v; }
    public boolean isSetContentStatus() { return setContentStatus; }
    public void setSetContentStatus(boolean v) { this.setContentStatus = v; }
    public boolean isSetWebStatus() { return setWebStatus; }
    public void setSetWebStatus(boolean v) { this.setWebStatus = v; }
    public Map<String, String> getStatusAttributes() { return statusAttributes; }
    public Map<String, String> getContentStatusByWorkflow() { return contentStatusByWorkflow; }
    public Map<String, String> getWebStatusByWorkflow() { return webStatusByWorkflow; }

    protected void parseStatusConfig(JsonObject obj) {
        if (obj == null) return;
        setContentStatusAttributes = getBool(obj, "setContentStatusAttributes", false);
        setContentStatus = getBool(obj, "setContentStatus", false);
        setWebStatus = getBool(obj, "setWebStatus", false);

        if (obj.has("statusAttributes")) {
            JsonObject attrs = obj.getAsJsonObject("statusAttributes");
            for (var entry : attrs.entrySet()) {
                statusAttributes.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        if (obj.has("contentStatusByWorkflow")) {
            JsonObject csw = obj.getAsJsonObject("contentStatusByWorkflow");
            for (var entry : csw.entrySet()) {
                contentStatusByWorkflow.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        if (obj.has("webStatusByWorkflow")) {
            JsonObject wsw = obj.getAsJsonObject("webStatusByWorkflow");
            for (var entry : wsw.entrySet()) {
                webStatusByWorkflow.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
    }

    protected static boolean getBool(JsonObject obj, String key, boolean defaultValue) {
        JsonElement e = obj.get(key);
        return e != null && !e.isJsonNull() ? e.getAsBoolean() : defaultValue;
    }

    protected static String getStr(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        return e != null && !e.isJsonNull() ? e.getAsString() : null;
    }

    protected static int getInt(JsonObject obj, String key, int defaultValue) {
        JsonElement e = obj.get(key);
        return e != null && !e.isJsonNull() ? e.getAsInt() : defaultValue;
    }

    protected static double getDouble(JsonObject obj, String key, double defaultValue) {
        JsonElement e = obj.get(key);
        return e != null && !e.isJsonNull() ? e.getAsDouble() : defaultValue;
    }
}
