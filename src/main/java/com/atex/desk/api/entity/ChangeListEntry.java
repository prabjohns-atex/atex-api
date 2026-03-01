package com.atex.desk.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "changelist")
public class ChangeListEntry
{
    @Id
    private Integer id;

    @Column(nullable = false)
    private Integer eventtype;

    @Column(nullable = false)
    private Integer idtype;

    @Column(length = 255, nullable = false)
    private String contentid;

    @Column(length = 255, nullable = false)
    private String version;

    @Column(length = 255, nullable = false)
    private String contenttype;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 255, nullable = false)
    private String createdBy;

    @Column(name = "modified_at", nullable = false)
    private Instant modifiedAt;

    @Column(name = "modified_by", length = 255, nullable = false)
    private String modifiedBy;

    @Column(name = "commit_at", nullable = false)
    private Instant commitAt;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getEventtype() { return eventtype; }
    public void setEventtype(Integer eventtype) { this.eventtype = eventtype; }

    public Integer getIdtype() { return idtype; }
    public void setIdtype(Integer idtype) { this.idtype = idtype; }

    public String getContentid() { return contentid; }
    public void setContentid(String contentid) { this.contentid = contentid; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getContenttype() { return contenttype; }
    public void setContenttype(String contenttype) { this.contenttype = contenttype; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Instant getModifiedAt() { return modifiedAt; }
    public void setModifiedAt(Instant modifiedAt) { this.modifiedAt = modifiedAt; }

    public String getModifiedBy() { return modifiedBy; }
    public void setModifiedBy(String modifiedBy) { this.modifiedBy = modifiedBy; }

    public Instant getCommitAt() { return commitAt; }
    public void setCommitAt(Instant commitAt) { this.commitAt = commitAt; }
}
