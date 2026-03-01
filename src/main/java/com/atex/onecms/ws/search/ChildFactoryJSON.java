package com.atex.onecms.ws.search;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gson-based ChildFactory for building JSON representations of Solr NamedList responses.
 * Ported from polopoly/core/data-api.
 */
public class ChildFactoryJSON implements ChildFactory<JsonElement> {

    private static final Logger LOG = Logger.getLogger(ChildFactoryJSON.class.getName());

    private final boolean writeMapsAsLists;
    private JsonElement root;

    public ChildFactoryJSON() {
        this(false);
    }

    public ChildFactoryJSON(final boolean writeMapsAsLists) {
        this.writeMapsAsLists = writeMapsAsLists;
    }

    @Override
    public JsonElement createElement(final String name, final String typeName) {
        if ("arr".equals(typeName)) {
            JsonObject wrapper = new JsonObject();
            wrapper.add(name != null ? name : "", new JsonArray());
            wrapper.addProperty("_type", "arr");
            return wrapper;
        }
        if ("lst".equals(typeName)) {
            if (writeMapsAsLists) {
                JsonObject wrapper = new JsonObject();
                wrapper.add(name != null ? name : "", new JsonArray());
                wrapper.addProperty("_type", "lst_as_arr");
                return wrapper;
            }
            JsonObject obj = new JsonObject();
            obj.addProperty("_name", name != null ? name : "");
            obj.addProperty("_type", "lst");
            return obj;
        }
        if ("doc".equals(typeName)) {
            JsonObject obj = new JsonObject();
            obj.addProperty("_name", name != null ? name : "");
            obj.addProperty("_type", "doc");
            return obj;
        }
        if ("result".equals(typeName)) {
            JsonObject obj = new JsonObject();
            obj.addProperty("_name", name != null ? name : "");
            obj.addProperty("_type", "result");
            obj.add("docs", new JsonArray());
            return obj;
        }
        // Default: simple value container
        JsonObject obj = new JsonObject();
        obj.addProperty("_name", name != null ? name : "");
        obj.addProperty("_type", typeName != null ? typeName : "str");
        return obj;
    }

    @Override
    public void appendChild(final JsonElement parent, final JsonElement child) {
        if (parent == null || child == null) return;

        if (parent.isJsonObject()) {
            JsonObject parentObj = parent.getAsJsonObject();
            String parentType = getTypeAttr(parentObj);

            if ("arr".equals(parentType)) {
                // Find the array element
                for (var entry : parentObj.entrySet()) {
                    if (!"_type".equals(entry.getKey()) && entry.getValue().isJsonArray()) {
                        JsonArray arr = entry.getValue().getAsJsonArray();
                        // Extract value from child
                        arr.add(extractValue(child));
                        return;
                    }
                }
            } else if ("lst_as_arr".equals(parentType)) {
                for (var entry : parentObj.entrySet()) {
                    if (!"_type".equals(entry.getKey()) && entry.getValue().isJsonArray()) {
                        entry.getValue().getAsJsonArray().add(extractValue(child));
                        return;
                    }
                }
            } else if ("result".equals(parentType)) {
                if (parentObj.has("docs") && parentObj.get("docs").isJsonArray()) {
                    parentObj.getAsJsonArray("docs").add(extractValue(child));
                    return;
                }
            } else if ("lst".equals(parentType) || "doc".equals(parentType)) {
                // Add child as a named property
                String childName = getName(child);
                if (childName != null && !childName.isEmpty()) {
                    parentObj.add(childName, extractValue(child));
                }
                return;
            }

            // Fallback: if it's the root object, add named children
            String childName = getName(child);
            if (childName != null && !childName.isEmpty()) {
                parentObj.add(childName, extractValue(child));
            }
        }
    }

    @Override
    public void appendChild(final JsonElement parent, final String value) {
        if (parent == null) return;

        if (parent.isJsonObject()) {
            JsonObject parentObj = parent.getAsJsonObject();
            String parentType = getTypeAttr(parentObj);

            if ("arr".equals(parentType) || "lst_as_arr".equals(parentType)) {
                for (var entry : parentObj.entrySet()) {
                    if (!"_type".equals(entry.getKey()) && entry.getValue().isJsonArray()) {
                        entry.getValue().getAsJsonArray().add(value != null ? value : "");
                        return;
                    }
                }
            }
            // For json type, try to parse as JSON
            String type = getTypeAttr(parentObj);
            if ("json".equals(type) || "str".equals(type)) {
                parentObj.addProperty("_value", value);
            }
        }
    }

    @Override
    public void appendChild(final JsonElement parent, final Number value) {
        if (parent == null) return;

        if (parent.isJsonObject()) {
            JsonObject parentObj = parent.getAsJsonObject();
            String parentType = getTypeAttr(parentObj);

            if ("arr".equals(parentType) || "lst_as_arr".equals(parentType)) {
                for (var entry : parentObj.entrySet()) {
                    if (!"_type".equals(entry.getKey()) && entry.getValue().isJsonArray()) {
                        entry.getValue().getAsJsonArray().add(value);
                        return;
                    }
                }
            }
        }
    }

    @Override
    public JsonElement getElement() {
        return root;
    }

    @Override
    public String getAttribute(final JsonElement element, final String name) {
        if (element != null && element.isJsonObject()) {
            JsonElement attr = element.getAsJsonObject().get(name);
            if (attr != null && attr.isJsonPrimitive()) {
                return attr.getAsString();
            }
        }
        return null;
    }

    @Override
    public void setAttribute(final JsonElement element, final String name, final String value) {
        if (element != null && element.isJsonObject()) {
            element.getAsJsonObject().addProperty(name, value);
        }
    }

    @Override
    public String getName(final JsonElement element) {
        if (element != null && element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("_name")) {
                return obj.get("_name").getAsString();
            }
            // For arr/lst_as_arr types, the name is the non-_type key
            for (var entry : obj.entrySet()) {
                if (!"_type".equals(entry.getKey())) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    @Override
    public void setName(final JsonElement element, final String name) {
        if (element != null && element.isJsonObject()) {
            element.getAsJsonObject().addProperty("_name", name);
        }
    }

    public void setRoot(final JsonElement root) {
        this.root = root;
    }

    private String getTypeAttr(final JsonObject obj) {
        if (obj.has("_type")) {
            return obj.get("_type").getAsString();
        }
        return null;
    }

    private JsonElement extractValue(final JsonElement element) {
        if (element == null) return JsonNull.INSTANCE;
        if (!element.isJsonObject()) return element;

        JsonObject obj = element.getAsJsonObject();
        String type = getTypeAttr(obj);

        if ("arr".equals(type) || "lst_as_arr".equals(type)) {
            for (var entry : obj.entrySet()) {
                if (!"_type".equals(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }
        if ("lst".equals(type) || "doc".equals(type)) {
            // Return a clean object without metadata
            JsonObject clean = new JsonObject();
            for (var entry : obj.entrySet()) {
                if (!"_type".equals(entry.getKey()) && !"_name".equals(entry.getKey())) {
                    clean.add(entry.getKey(), entry.getValue());
                }
            }
            return clean;
        }
        if ("result".equals(type)) {
            JsonObject clean = new JsonObject();
            for (var entry : obj.entrySet()) {
                if (!"_type".equals(entry.getKey()) && !"_name".equals(entry.getKey())) {
                    clean.add(entry.getKey(), entry.getValue());
                }
            }
            return clean;
        }
        if (obj.has("_value")) {
            return obj.get("_value");
        }

        // Return as-is minus metadata
        JsonObject clean = new JsonObject();
        for (var entry : obj.entrySet()) {
            if (!"_type".equals(entry.getKey()) && !"_name".equals(entry.getKey())) {
                clean.add(entry.getKey(), entry.getValue());
            }
        }
        return clean;
    }
}
