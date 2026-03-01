package com.atex.desk.api.entity;

import java.io.Serializable;
import java.util.Objects;

public class AppGroupMemberId implements Serializable
{
    private Integer groupId;
    private String principalId;

    public AppGroupMemberId()
    {
    }

    public AppGroupMemberId(Integer groupId, String principalId)
    {
        this.groupId = groupId;
        this.principalId = principalId;
    }

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

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AppGroupMemberId that = (AppGroupMemberId) o;
        return Objects.equals(groupId, that.groupId) && Objects.equals(principalId, that.principalId);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(groupId, principalId);
    }
}
