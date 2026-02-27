package com.atex.desk.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "idaliases")
@IdClass(ContentAliasId.class)
public class ContentAlias
{
    @Id
    @Column(nullable = false)
    private Integer idtype;

    @Id
    @Column(length = 255, nullable = false)
    private String id;

    @Id
    @Column(name = "aliasid", nullable = false)
    private Integer aliasId;

    @Column(length = 255, nullable = false)
    private String value;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 255, nullable = false)
    private String createdBy;

    public Integer getIdtype() { return idtype; }
    public void setIdtype(Integer idtype) { this.idtype = idtype; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Integer getAliasId() { return aliasId; }
    public void setAliasId(Integer aliasId) { this.aliasId = aliasId; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
