package com.atex.desk.api.entity;

import java.io.Serializable;
import java.util.Objects;

public class AppAclOwnerId implements Serializable
{
    private Integer aclId;
    private String principalId;

    public AppAclOwnerId()
    {
    }

    public AppAclOwnerId(Integer aclId, String principalId)
    {
        this.aclId = aclId;
        this.principalId = principalId;
    }

    public Integer getAclId()
    {
        return aclId;
    }

    public void setAclId(Integer aclId)
    {
        this.aclId = aclId;
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
        AppAclOwnerId that = (AppAclOwnerId) o;
        return Objects.equals(aclId, that.aclId) && Objects.equals(principalId, that.principalId);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(aclId, principalId);
    }
}
