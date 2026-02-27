package com.polopoly.metadata;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a dimension of entities (e.g. locations, IPTC subjects).
 */
public class Dimension {
    private String id;
    private String name;
    private boolean enumerable;
    private List<Entity> entities;

    public Dimension() {
        this.entities = new ArrayList<>();
    }

    public Dimension(String id, String name, boolean enumerable) {
        this(id, name, enumerable, new ArrayList<>());
    }

    public Dimension(String id, String name, boolean enumerable, List<Entity> entities) {
        this.id = id;
        this.name = name;
        this.enumerable = enumerable;
        this.entities = entities != null ? entities : new ArrayList<>();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isEnumerable() { return enumerable; }
    public void setEnumerable(boolean enumerable) { this.enumerable = enumerable; }
    public List<Entity> getEntities() { return entities; }
    public void setEntities(List<Entity> entities) { this.entities = entities; }

    public void addEntities(Entity... entities) {
        if (this.entities == null) this.entities = new ArrayList<>();
        for (Entity e : entities) {
            this.entities.add(e);
        }
    }
}
