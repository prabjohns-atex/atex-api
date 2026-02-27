package com.atex.desk.api.entity;

import java.io.Serializable;
import java.util.Objects;

public class ContentAttributeId implements Serializable
{
    private String id;
    private Integer attrId;

    public ContentAttributeId() {}

    public ContentAttributeId(String id, Integer attrId)
    {
        this.id = id;
        this.attrId = attrId;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Integer getAttrId() { return attrId; }
    public void setAttrId(Integer attrId) { this.attrId = attrId; }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof ContentAttributeId that)) return false;
        return Objects.equals(id, that.id)
            && Objects.equals(attrId, that.attrId);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(id, attrId);
    }
}
