package com.atex.onecms.app.dam.lifecycle.charcount;

import java.util.List;

/**
 * Configuration for OneCharCountPreStoreHook.
 * Specifies which text fields to count characters from.
 */
public class OneCharCountPreStoreConfig {
    private List<String> fields;

    public OneCharCountPreStoreConfig() {
        this.fields = List.of("body");
    }

    public OneCharCountPreStoreConfig(List<String> fields) {
        this.fields = fields;
    }

    public List<String> getFields() { return fields; }
    public void setFields(List<String> fields) { this.fields = fields; }
}
