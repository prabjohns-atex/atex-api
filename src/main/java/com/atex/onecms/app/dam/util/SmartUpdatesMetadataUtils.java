package com.atex.onecms.app.dam.util;

import com.atex.onecms.content.metadata.MetadataInfo;
import com.polopoly.metadata.Dimension;
import com.polopoly.metadata.Entity;
import com.polopoly.metadata.Metadata;

import java.util.HashSet;
import java.util.Set;

public class SmartUpdatesMetadataUtils {

    public static final String ATEX_METADATA = "atex.Metadata";

    public static void updateDimension(Dimension dimension, String field, String value) {
        boolean duplicate = false;
        for (Entity entity : dimension.getEntities()) {
            if (entity.getId().equalsIgnoreCase(value)) {
                duplicate = true;
                break;
            }
        }
        if (!duplicate) {
            Entity entity = new Entity();
            entity.setId(value);
            entity.setName(value);
            dimension.addEntities(entity);
        }
    }

    public static Dimension createDimension(String field, String value) {
        Dimension dimension = new Dimension();
        dimension.setId(field);
        String name = field.contains("dimension.") ? field.split("dimension\\.")[1] : field;
        dimension.setName(name);
        Entity entity = new Entity();
        entity.setId(value);
        entity.setName(value);
        dimension.addEntities(entity);
        return dimension;
    }

    public static Metadata createMetadata(String field, String value) {
        Metadata metadata = new Metadata();
        Dimension dimension = createDimension(field, value);
        metadata.addDimension(dimension);
        return metadata;
    }

    public static MetadataInfo createMetadataInfo(String field, String value) {
        MetadataInfo info = new MetadataInfo();
        info.setMetadata(createMetadata(field, value));
        return info;
    }
}
