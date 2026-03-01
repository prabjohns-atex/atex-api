package com.atex.desk.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "groupMember")
@IdClass(AppGroupMemberId.class)
public class AppGroupMember
{
    @Id
    @Column(name = "groupId")
    private Integer groupId;

    @Id
    @Column(name = "principalId", length = 32)
    private String principalId;

    public Integer getGroupId()
    {
        return groupId;
    }

    public void setGroupId(Integer groupId)
    {
        this.groupId = groupId;
    }

    public String getPrincipalId()
    {
        return principalId;
    }

    public void setPrincipalId(String principalId)
    {
        this.principalId = principalId;
    }
}
