package com.atex.desk.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "id")
public class ContentId
{
    @Id
    @Column(length = 255)
    private String id;

    @Column(nullable = false)
    private Integer idtype;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 255, nullable = false)
    private String createdBy;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Integer getIdtype() { return idtype; }
    public void setIdtype(Integer idtype) { this.idtype = idtype; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
