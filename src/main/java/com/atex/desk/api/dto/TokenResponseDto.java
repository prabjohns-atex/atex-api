package com.atex.desk.api.dto;

public class TokenResponseDto
{
    private String token;
    private String userId;
    private String expireTime;
    private String renewTime;

    public String getToken()
    {
        return token;
    }

    public void setToken(String token)
    {
        this.token = token;
    }

    public String getUserId()
    {
        return userId;
    }

    public void setUserId(String userId)
    {
        this.userId = userId;
    }

    public String getExpireTime()
    {
        return expireTime;
    }

    public void setExpireTime(String expireTime)
    {
        this.expireTime = expireTime;
    }

    public String getRenewTime()
    {
        return renewTime;
    }

    public void setRenewTime(String renewTime)
    {
        this.renewTime = renewTime;
    }
}
