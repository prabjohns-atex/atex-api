package com.atex.onecms.app.dam.propertybag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * PropertyBagConfiguration
 *
 * @author rdemattei
 */
public class PropertyBagConfiguration {

    private static final Logger LOGGER = Logger.getLogger(PropertyBagConfiguration.class.getName());

    private final List<SchemaField> fields = new ArrayList<>();
    private final Map<String, String> otherProperties = new HashMap<>();

    public List<SchemaField> getFields() {
        return fields;
    }

    public Map<String, String> getOtherProperties() {
        return otherProperties;
    }

    public void setFields(final List<SchemaField> fields) {
        this.fields.clear();
        if (fields != null) {
            this.fields.addAll(fields);
        }
    }

    public void setOtherProperties(final Map<String, String> otherProperties) {
        this.otherProperties.clear();
        if (otherProperties != null) {
            this.otherProperties.putAll(otherProperties);
        }
    }
}
