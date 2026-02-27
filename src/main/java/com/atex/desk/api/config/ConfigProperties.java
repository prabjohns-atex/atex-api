package com.atex.desk.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "desk.config")
public class ConfigProperties
{
    private boolean enabled = true;
    private String flavor = "";

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    public String getFlavor()
    {
        return flavor;
    }

    public void setFlavor(String flavor)
    {
        this.flavor = flavor;
    }
}
