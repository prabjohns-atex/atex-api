package com.atex.desk.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "aclData")
public class AppAcl
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "name", nullable = false, length = 32)
    private String name;

    @Column(name = "creationTime", nullable = false)
    private int creationTime;

    @Column(name = "firstOwnerId", length = 32)
    private String firstOwnerId;

    public Integer getId()
    {
        return id;
    }

    public void setId(Integer id)
    {
        this.id = id;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public int getCreationTime()
    {
        return creationTime;
    }

    public void setCreationTime(int creationTime)
    {
        this.creationTime = creationTime;
    }

    public String getFirstOwnerId()
    {
        return firstOwnerId;
    }

    public void setFirstOwnerId(String firstOwnerId)
    {
        this.firstOwnerId = firstOwnerId;
    }
}
