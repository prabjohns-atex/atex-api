package com.atex.onecms.app.dam.metadata;

import com.atex.onecms.content.aspects.annotations.AspectDefinition;

import java.util.List;

@AspectDefinition(DamTag.ASPECT_NAME)
public class DamTag {
    public static final String ASPECT_NAME = "atex.onecms.metadata.Tag";

    String dimId;
    String id;
    String name;
    List<String> parent;
    List<DamTagMetadata> metadata;
    String listId;
    String creator;
    boolean approved;
    boolean converted;

    public String getDimId() { return dimId; }
    public void setDimId(String dimId) { this.dimId = dimId; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<String> getParent() { return parent; }
    public void setParent(List<String> parent) { this.parent = parent; }

    public List<DamTagMetadata> getMetadata() { return metadata; }
    public void setMetadata(List<DamTagMetadata> metadata) { this.metadata = metadata; }

    public String getListId() { return listId; }
    public void setListId(String listId) { this.listId = listId; }

    public String getCreator() { return creator; }
    public void setCreator(String creator) { this.creator = creator; }

    public boolean isApproved() { return approved; }
    public void setApproved(boolean approved) { this.approved = approved; }

    public boolean isConverted() { return converted; }
    public void setConverted(boolean converted) { this.converted = converted; }
}
