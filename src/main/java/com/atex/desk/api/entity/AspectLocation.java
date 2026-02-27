package com.atex.desk.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "aspectslocations")
@IdClass(AspectLocationId.class)
public class AspectLocation
{
    @Id
    @Column(name = "contentid")
    private Integer contentId;

    @Id
    @Column(name = "aspectid")
    private Integer aspectId;

    public Integer getContentId() { return contentId; }
    public void setContentId(Integer contentId) { this.contentId = contentId; }

    public Integer getAspectId() { return aspectId; }
    public void setAspectId(Integer aspectId) { this.aspectId = aspectId; }
}
