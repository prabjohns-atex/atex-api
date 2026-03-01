package com.atex.desk.api.entity;

import java.io.Serializable;
import java.util.Objects;

public class ChangeListAttributeId implements Serializable
{
    private Integer id;
    private Integer attrId;

    public ChangeListAttributeId() {}

    public ChangeListAttributeId(Integer id, Integer attrId) {
        this.id = id;
        this.attrId = attrId;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getAttrId() { return attrId; }
    public void setAttrId(Integer attrId) { this.attrId = attrId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChangeListAttributeId that)) return false;
        return Objects.equals(id, that.id) && Objects.equals(attrId, that.attrId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, attrId);
    }
}
