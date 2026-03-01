package com.atex.desk.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "acl")
@IdClass(AppAclEntryId.class)
public class AppAclEntry
{
    @Id
    @Column(name = "aclId")
    private Integer aclId;

    @Id
    @Column(name = "principalId", length = 32)
    private String principalId;

    @Id
    @Column(name = "permission", length = 32)
    private String permission;

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
}
