package com.atex.onecms.app.dam.lifecycle.wordcount;

import java.util.List;

/**
 * Configuration for OneWordCountPreStoreHook.
 * Specifies which text fields to count words from.
 */
public class OneWordCountPreStoreConfig {
    private List<String> fields;

    public OneWordCountPreStoreConfig() {
        this.fields = List.of("body");
    }

    public OneWordCountPreStoreConfig(List<String> fields) {
        this.fields = fields;
    }

    public List<String> getFields() { return fields; }
    public void setFields(List<String> fields) { this.fields = fields; }
}
