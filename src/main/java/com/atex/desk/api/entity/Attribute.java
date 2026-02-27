package com.atex.desk.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "attributes")
public class Attribute
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attrid")
    private Integer attrId;

    @Column(length = 32, nullable = false, unique = true)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Integer getAttrId() { return attrId; }
    public void setAttrId(Integer attrId) { this.attrId = attrId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
