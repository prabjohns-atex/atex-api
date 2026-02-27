package com.atex.desk.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Authentication token response")
public class TokenResponseDto
{
    @Schema(description = "The JWT token")
    private String token;

    @Schema(description = "The user ID")
    private String userId;

    @Schema(description = "Token expiration time (epoch milliseconds)")
    private String expireTime;

    @Schema(description = "Token renewal time (epoch milliseconds)")
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
