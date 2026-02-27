package com.atex.desk.api.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "desk.auth")
public class TokenProperties
{
    private boolean enabled = true;
    private String privateKey;
    private String publicKey;
    private String algo = "RS256";
    private long maxLifetime = 86400;
    private long clockSkew = 30;
    private String instanceId = "desk-api-dev";

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    public String getPrivateKey()
    {
        return privateKey;
    }

    public void setPrivateKey(String privateKey)
    {
        this.privateKey = privateKey;
    }

    public String getPublicKey()
    {
        return publicKey;
    }

    public void setPublicKey(String publicKey)
    {
        this.publicKey = publicKey;
    }

    public String getAlgo()
    {
        return algo;
    }

    public void setAlgo(String algo)
    {
        this.algo = algo;
    }

    public long getMaxLifetime()
    {
        return maxLifetime;
    }

    public void setMaxLifetime(long maxLifetime)
    {
        this.maxLifetime = maxLifetime;
    }

    public long getClockSkew()
    {
        return clockSkew;
    }

    public void setClockSkew(long clockSkew)
    {
        this.clockSkew = clockSkew;
    }

    public String getInstanceId()
    {
        return instanceId;
    }

    public void setInstanceId(String instanceId)
    {
        this.instanceId = instanceId;
    }
}
