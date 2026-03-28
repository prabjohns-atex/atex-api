package com.atex.onecms.app.dam.propertybag;

import java.util.List;
import java.util.Map;

public class SchemaField {
    private String id;
    private String group;
    private String name;
    private String type;
    private boolean isStock;
    private List<String> contentType;
    private boolean indexed;
    private String indexField;
    private boolean multiple;
    private List<String> entries;
    private Object defaultValue;
    private Map<String, Object> displayProperties;

    public enum Type {
        NUMBER("number"),
        BOOLEAN("boolean"),
        STRING("string"),
        TEXT("text"),
        DATE("date");

        private final String value;

        Type(String value) { this.value = value; }

        @Override
        public String toString() {
            return value;
        }

        public String getValue() {
            return value;
        }

        public static Type fromValue(String v) {
            for (Type type : Type.values()) {
                if (type.value.equalsIgnoreCase(v)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Invalid value: " + v);
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public boolean isStock() { return isStock; }
    public void setStock(boolean stock) { isStock = stock; }

    public List<String> getContentType() { return contentType; }
    public void setContentType(List<String> contentType) { this.contentType = contentType; }

    public boolean isIndexed() { return indexed; }
    public void setIndexed(boolean indexed) { this.indexed = indexed; }

    public String getIndexField() { return indexField; }
    public void setIndexField(String indexField) { this.indexField = indexField; }

    public boolean isMultiple() { return multiple; }
    public void setMultiple(boolean multiple) { this.multiple = multiple; }

    public List<String> getEntries() { return entries; }
    public void setEntries(List<String> entries) { this.entries = entries; }

    public Object getDefaultValue() { return defaultValue; }
    public void setDefaultValue(Object defaultValue) { this.defaultValue = defaultValue; }

    public Map<String, Object> getDisplayProperties() { return displayProperties; }
    public void setDisplayProperties(Map<String, Object> displayProperties) { this.displayProperties = displayProperties; }
}
