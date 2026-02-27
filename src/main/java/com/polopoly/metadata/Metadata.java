package com.polopoly.metadata;

import java.util.ArrayList;
import java.util.List;

/**
 * Collection of Dimension objects.
 */
public class Metadata {
    private List<Dimension> dimensions;

    public Metadata() {
        this.dimensions = new ArrayList<>();
    }

    public Metadata(List<Dimension> dimensions) {
        this.dimensions = dimensions != null ? dimensions : new ArrayList<>();
    }

    public List<Dimension> getDimensions() { return dimensions; }
    public void setDimensions(List<Dimension> dimensions) { this.dimensions = dimensions; }

    public Dimension getDimensionById(String id) {
        if (id == null || dimensions == null) return null;
        for (Dimension d : dimensions) {
            if (id.equals(d.getId())) return d;
        }
        return null;
    }

    public void addDimension(Dimension dimension) {
        if (dimensions == null) dimensions = new ArrayList<>();
        dimensions.add(dimension);
    }

    public void replaceDimension(Dimension dimension) {
        if (dimensions == null) { dimensions = new ArrayList<>(); }
        dimensions.removeIf(d -> d.getId() != null && d.getId().equals(dimension.getId()));
        dimensions.add(dimension);
    }
}
