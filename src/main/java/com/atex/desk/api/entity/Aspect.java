package com.atex.desk.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "aspects")
public class Aspect
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "aspectid")
    private Integer aspectId;

    @Column(name = "versionid", nullable = false)
    private Integer versionId;

    @Column(name = "contentid", length = 255, nullable = false)
    private String contentId;

    @Column(length = 255, nullable = false)
    private String name;

    @Column(columnDefinition = "JSON", nullable = false)
    private String data;

    @Column(length = 32, nullable = false, columnDefinition = "CHAR(32)")
    private String md5;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 255, nullable = false)
    private String createdBy;

    public Integer getAspectId() { return aspectId; }
    public void setAspectId(Integer aspectId) { this.aspectId = aspectId; }

    public Integer getVersionId() { return versionId; }
    public void setVersionId(Integer versionId) { this.versionId = versionId; }

    public String getContentId() { return contentId; }
    public void setContentId(String contentId) { this.contentId = contentId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getData() { return data; }
    public void setData(String data) { this.data = data; }

    public String getMd5() { return md5; }
    public void setMd5(String md5) { this.md5 = md5; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
