package com.polopoly.metadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * An entity within a Dimension. May have child entities (hierarchical taxonomy)
 * and localizations (language-specific names).
 */
public class Entity {
    private String id;
    private String name;
    private List<Entity> entities;
    private Map<String, String> localizations;

    public Entity() {}

    public Entity(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<Entity> getEntities() { return entities; }
    public void setEntities(List<Entity> entities) { this.entities = entities; }

    public void addEntity(Entity entity) {
        if (this.entities == null) this.entities = new ArrayList<>();
        this.entities.add(entity);
    }

    public Map<String, String> getLocalizations() { return localizations; }
    public void setLocalizations(Map<String, String> localizations) { this.localizations = localizations; }
}
