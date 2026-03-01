package com.atex.desk.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "groupData")
public class AppGroup
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer groupId;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Column(name = "creationTime", nullable = false)
    private int creationTime;

    @Column(name = "firstOwnerId", length = 32)
    private String firstOwnerId;

    @Column(name = "ldapGroupDn")
    private String ldapGroupDn;

    @Column(name = "remoteGroupDn")
    private String remoteGroupDn;

    @Column(name = "remoteServiceId")
    private String remoteServiceId;

    public Integer getGroupId()
    {
        return groupId;
    }

    public void setGroupId(Integer groupId)
    {
        this.groupId = groupId;
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

    public String getLdapGroupDn()
    {
        return ldapGroupDn;
    }

    public void setLdapGroupDn(String ldapGroupDn)
    {
        this.ldapGroupDn = ldapGroupDn;
    }

    public String getRemoteGroupDn()
    {
        return remoteGroupDn;
    }

    public void setRemoteGroupDn(String remoteGroupDn)
    {
        this.remoteGroupDn = remoteGroupDn;
    }

    public String getRemoteServiceId()
    {
        return remoteServiceId;
    }

    public void setRemoteServiceId(String remoteServiceId)
    {
        this.remoteServiceId = remoteServiceId;
    }
}
