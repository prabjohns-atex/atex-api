package com.atex.desk.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "aclOwner")
@IdClass(AppAclOwnerId.class)
public class AppAclOwner
{
    @Id
    @Column(name = "aclId")
    private Integer aclId;

    @Id
    @Column(name = "principalId", length = 32)
    private String principalId;

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
}
