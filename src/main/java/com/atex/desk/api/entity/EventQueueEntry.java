package com.atex.desk.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "eventsqueue")
public class EventQueueEntry
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private Integer eventtype;

    @Column(nullable = false)
    private Integer versionid;

    private Integer viewid;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 255, nullable = false)
    private String createdBy;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getEventtype() { return eventtype; }
    public void setEventtype(Integer eventtype) { this.eventtype = eventtype; }

    public Integer getVersionid() { return versionid; }
    public void setVersionid(Integer versionid) { this.versionid = versionid; }

    public Integer getViewid() { return viewid; }
    public void setViewid(Integer viewid) { this.viewid = viewid; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
