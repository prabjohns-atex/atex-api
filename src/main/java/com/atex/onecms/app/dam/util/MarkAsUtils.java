package com.atex.onecms.app.dam.util;

import com.atex.onecms.content.metadata.MetadataInfo;
import com.polopoly.metadata.Dimension;
import com.polopoly.metadata.Entity;
import com.polopoly.metadata.Metadata;

import java.util.ArrayList;
import java.util.List;

public class MarkAsUtils {

    public static final String DIMENSION_ID_PREFIX = "dimension.markas-";
    public static final String ATEX_METADATA = "atex.Metadata";

    public static void updateDimension(Dimension dimension, String value) {
        List<Entity> entities = new ArrayList<>();
        Entity entity = new Entity();
        entity.setId(value);
        entity.setName(value);
        entities.add(entity);
        dimension.setEntities(entities);
    }

    public static Dimension createDimension(String dimensionId, String value) {
        Dimension dimension = new Dimension();
        dimension.setId(dimensionId);
        dimension.setName(dimensionId);
        Entity entity = new Entity();
        entity.setId(value);
        entity.setName(value);
        dimension.addEntities(entity);
        return dimension;
    }

    public static String getDimensionId(String markasField) {
        return DIMENSION_ID_PREFIX + markasField;
    }

    public static MetadataInfo createMetadataInfo(String name, String value) {
        MetadataInfo info = new MetadataInfo();
        info.setMetadata(createMetadata(name, value));
        return info;
    }

    public static Metadata createMetadata(String name, String value) {
        Metadata metadata = new Metadata();
        Dimension dimension = createDimension(name, value);
        metadata.addDimension(dimension);
        return metadata;
    }
}
