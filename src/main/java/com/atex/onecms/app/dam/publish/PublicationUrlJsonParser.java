package com.atex.onecms.app.dam.publish;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

public class PublicationUrlJsonParser {
    private static final Gson GSON = new Gson();

    public static String getUrl(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            JsonElement element = JsonParser.parseString(json);
            if (element.isJsonPrimitive()) {
                return element.getAsString();
            }
            if (element.isJsonObject()) {
                JsonObject obj = element.getAsJsonObject();
                if (obj.has("url")) {
                    return obj.get("url").getAsString();
                }
            }
        } catch (Exception e) {
            // Not valid JSON, return as-is
            return json;
        }
        return null;
    }

    public static String toUrl(String url) {
        if (url == null) return null;
        JsonObject obj = new JsonObject();
        obj.addProperty("url", url);
        return GSON.toJson(obj);
    }
}
