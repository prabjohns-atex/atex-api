package com.atex.desk.api.config;

import com.atex.onecms.content.ConfigurationDataBean;
import com.google.gson.Gson;

import java.util.List;
import java.util.Map;

/**
 * A configuration entry holding both the config data and its metadata.
 *
 * @param data the parsed configuration data (without the _meta block)
 * @param meta metadata extracted from the _meta block (display name, etc.)
 * @param contentList optional content list references (extracted from _contentList array)
 */
public record ConfigEntry(Map<String, Object> data, ConfigMeta meta, List<String> contentList)
{
    private static final Gson GSON = new Gson();

    /**
     * Whether this entry has a display name that differs from the external ID.
     */
    public boolean hasName()
    {
        return meta != null && meta.name() != null && !meta.name().contains(".");
    }

    /**
     * Build a {@link ConfigurationDataBean} wrapping this entry's data,
     * matching the reference Polopoly p.ConfigurationData content type.
     *
     * @param externalId used as fallback name if meta has none
     */
    public ConfigurationDataBean toConfigurationDataBean(String externalId)
    {
        String jsonString = GSON.toJson(data);
        ConfigurationDataBean bean = new ConfigurationDataBean();
        bean.setName(meta != null ? meta.name() : externalId);
        bean.setJson(jsonString);
        bean.setDataType("json");
        bean.setDataValue(jsonString);
        return bean;
    }
}