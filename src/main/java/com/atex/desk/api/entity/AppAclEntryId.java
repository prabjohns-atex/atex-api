package com.atex.desk.api.entity;

import java.io.Serializable;
import java.util.Objects;

public class AppAclEntryId implements Serializable
{
    private Integer aclId;
    private String principalId;
    private String permission;

    public AppAclEntryId()
    {
    }

    public AppAclEntryId(Integer aclId, String principalId, String permission)
    {
        this.aclId = aclId;
        this.principalId = principalId;
        this.permission = permission;
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

    public String getPermission()
    {
        return permission;
    }

    public void setPermission(String permission)
    {
        this.permission = permission;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AppAclEntryId that = (AppAclEntryId) o;
        return Objects.equals(aclId, that.aclId)
            && Objects.equals(principalId, that.principalId)
            && Objects.equals(permission, that.permission);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(aclId, principalId, permission);
    }
}
