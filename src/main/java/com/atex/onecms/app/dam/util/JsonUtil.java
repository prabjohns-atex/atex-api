package com.atex.onecms.app.dam.util;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
public class JsonUtil {
    public static Object convert(Object object) {
        if (object instanceof JsonPrimitive p) {
            if (p.isBoolean()) return p.getAsBoolean();
            if (p.isNumber()) return p.getAsNumber();
            return p.getAsString();
        }
        if (object instanceof JsonObject o) return toMap(o);
        if (object instanceof JsonArray a) return toList(a);
        return object;
    }
    public static Map<String, Object> toMap(JsonObject obj) {
        Map<String, Object> map = new HashMap<>();
        for (var entry : obj.entrySet()) {
            if (!entry.getValue().isJsonNull()) map.put(entry.getKey(), convert(entry.getValue()));
        }
        return map;
    }
    public static List<Object> toList(JsonArray array) {
        List<Object> list = new ArrayList<>();
        for (JsonElement el : array) {
            if (!el.isJsonNull()) list.add(convert(el));
        }
        return list;
    }

    public static JsonArray toJsonArray(List<String> list) {
        JsonArray arr = new JsonArray();
        for (String s : list) {
            arr.add(s);
        }
        return arr;
    }

    public static boolean isNotNull(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull();
    }
}
