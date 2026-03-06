package com.atex.desk.api.config;

import java.util.Map;

/**
 * Metadata extracted from the {@code _meta} block in a config JSON file.
 *
 * @param name   display name for the configuration (falls back to external ID if not set in the file)
 * @param group  grouping key for UI tabs (e.g., "UI", "Templates", "Publishing")
 * @param format output format: "raw" serves data directly in contentData.data (for templates),
 *               null/absent uses ConfigurationDataBean wrapping (default for configs)
 */
public record ConfigMeta(String name, String group, String format)
{
    /**
     * Whether this config should be served as an OneCMSTemplateBean (data field with stringified JSON).
     */
    public boolean isTemplateFormat()
    {
        return "atex.onecms.Template.it".equals(format);
    }

    /**
     * Whether this config should be served as an OneCMSTemplateListBean (templateList array).
     */
    public boolean isTemplateListFormat()
    {
        return "atex.onecms.TemplateList.it".equals(format);
    }

    /**
     * Whether this config uses a non-default format (not ConfigurationDataBean).
     */
    public boolean hasCustomFormat()
    {
        return format != null;
    }

    /**
     * Extract _meta from a parsed data map, removing it from the map.
     * Falls back to the external ID as the display name if no _meta.name is found.
     */
    static ConfigMeta fromData(Map<String, Object> data, String externalId)
    {
        String name = externalId;
        String group = null;
        String format = null;

        Object metaObj = data.remove("_meta");
        if (metaObj instanceof Map<?, ?> meta)
        {
            Object nameObj = meta.get("name");
            if (nameObj instanceof String s && !s.isBlank())
            {
                name = s;
            }
            Object groupObj = meta.get("group");
            if (groupObj instanceof String s && !s.isBlank())
            {
                group = s;
            }
            Object formatObj = meta.get("format");
            if (formatObj instanceof String s && !s.isBlank())
            {
                format = s;
            }
        }

        return new ConfigMeta(name, group, format);
    }
}