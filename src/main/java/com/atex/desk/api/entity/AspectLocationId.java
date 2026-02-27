package com.atex.desk.api.entity;

import java.io.Serializable;
import java.util.Objects;

public class AspectLocationId implements Serializable
{
    private Integer contentId;
    private Integer aspectId;

    public AspectLocationId() {}

    public AspectLocationId(Integer contentId, Integer aspectId)
    {
        this.contentId = contentId;
        this.aspectId = aspectId;
    }

    public Integer getContentId() { return contentId; }
    public void setContentId(Integer contentId) { this.contentId = contentId; }

    public Integer getAspectId() { return aspectId; }
    public void setAspectId(Integer aspectId) { this.aspectId = aspectId; }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof AspectLocationId that)) return false;
        return Objects.equals(contentId, that.contentId)
            && Objects.equals(aspectId, that.aspectId);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(contentId, aspectId);
    }
}
