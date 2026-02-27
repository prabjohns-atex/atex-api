package com.atex.desk.api.entity;

import java.io.Serializable;
import java.util.Objects;

public class ContentAliasId implements Serializable
{
    private Integer idtype;
    private String id;
    private Integer aliasId;

    public ContentAliasId() {}

    public ContentAliasId(Integer idtype, String id, Integer aliasId)
    {
        this.idtype = idtype;
        this.id = id;
        this.aliasId = aliasId;
    }

    public Integer getIdtype() { return idtype; }
    public void setIdtype(Integer idtype) { this.idtype = idtype; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Integer getAliasId() { return aliasId; }
    public void setAliasId(Integer aliasId) { this.aliasId = aliasId; }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof ContentAliasId that)) return false;
        return Objects.equals(idtype, that.idtype)
            && Objects.equals(id, that.id)
            && Objects.equals(aliasId, that.aliasId);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(idtype, id, aliasId);
    }
}
