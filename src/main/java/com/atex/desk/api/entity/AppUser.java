package com.atex.desk.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "registeredusers")
public class AppUser
{
    @Id
    @Column(name = "loginname", nullable = false, length = 64)
    private String loginName;

    @Column(name = "passwordhash")
    private String passwordHash;

    @Column(name = "regtime", nullable = false)
    private int regTime;

    @Column(name = "isldapuser", nullable = false)
    private int isLdapUser;

    @Column(name = "isremoteuser", nullable = false)
    private int isRemoteUser;

    @Column(name = "remoteserviceid")
    private String remoteServiceId;

    @Column(name = "remoteloginnames")
    private String remoteLoginNames;

    @Column(name = "lastlogintime", nullable = false)
    private int lastLoginTime;

    @Column(name = "numlogins", nullable = false)
    private int numLogins;

    @Column(name = "active", nullable = false)
    private int active;

    public String getLoginName()
    {
        return loginName;
    }

    public void setLoginName(String loginName)
    {
        this.loginName = loginName;
    }

    public String getPasswordHash()
    {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash)
    {
        this.passwordHash = passwordHash;
    }

    public int getRegTime()
    {
        return regTime;
    }

    public void setRegTime(int regTime)
    {
        this.regTime = regTime;
    }

    public int getIsLdapUser()
    {
        return isLdapUser;
    }

    public void setIsLdapUser(int isLdapUser)
    {
        this.isLdapUser = isLdapUser;
    }

    public int getIsRemoteUser()
    {
        return isRemoteUser;
    }

    public void setIsRemoteUser(int isRemoteUser)
    {
        this.isRemoteUser = isRemoteUser;
    }

    public String getRemoteServiceId()
    {
        return remoteServiceId;
    }

    public void setRemoteServiceId(String remoteServiceId)
    {
        this.remoteServiceId = remoteServiceId;
    }

    public String getRemoteLoginNames()
    {
        return remoteLoginNames;
    }

    public void setRemoteLoginNames(String remoteLoginNames)
    {
        this.remoteLoginNames = remoteLoginNames;
    }

    public int getLastLoginTime()
    {
        return lastLoginTime;
    }

    public void setLastLoginTime(int lastLoginTime)
    {
        this.lastLoginTime = lastLoginTime;
    }

    public int getNumLogins()
    {
        return numLogins;
    }

    public void setNumLogins(int numLogins)
    {
        this.numLogins = numLogins;
    }

    public int getActive()
    {
        return active;
    }

    public void setActive(int active)
    {
        this.active = active;
    }

    public boolean isActive()
    {
        return active != 0;
    }

    public boolean isLdap()
    {
        return isLdapUser != 0;
    }

    public boolean isRemote()
    {
        return isRemoteUser != 0;
    }
}
