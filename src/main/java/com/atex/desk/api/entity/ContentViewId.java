package com.atex.desk.api.entity;

import java.io.Serializable;
import java.util.Objects;

public class ContentViewId implements Serializable
{
    private Integer versionId;
    private Integer viewId;

    public ContentViewId() {}

    public ContentViewId(Integer versionId, Integer viewId)
    {
        this.versionId = versionId;
        this.viewId = viewId;
    }

    public Integer getVersionId() { return versionId; }
    public void setVersionId(Integer versionId) { this.versionId = versionId; }

    public Integer getViewId() { return viewId; }
    public void setViewId(Integer viewId) { this.viewId = viewId; }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof ContentViewId that)) return false;
        return Objects.equals(versionId, that.versionId)
            && Objects.equals(viewId, that.viewId);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(versionId, viewId);
    }
}
