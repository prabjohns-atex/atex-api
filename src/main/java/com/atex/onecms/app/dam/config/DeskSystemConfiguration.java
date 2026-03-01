package com.atex.onecms.app.dam.config;

import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.Subject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class DeskSystemConfiguration {

    private static final String EXT_ID = "atex.configuration.desk-system-configuration";

    private Map<String, Object> properties = new LinkedHashMap<>();
    private Map<String, String> plausibleDomains = new LinkedHashMap<>();
    private Map<String, String> plausibleEmbeds = new LinkedHashMap<>();
    private String plausibleDefaultEmbed;
    private boolean usePlausibleEmbed;

    public Map<String, Object> getProperties() { return properties; }
    public void setProperties(Map<String, Object> v) { this.properties = v; }

    public Map<String, String> getPlausibleDomains() { return plausibleDomains; }
    public void setPlausibleDomains(Map<String, String> v) { this.plausibleDomains = v; }

    public Map<String, String> getPlausibleEmbeds() { return plausibleEmbeds; }
    public void setPlausibleEmbeds(Map<String, String> v) { this.plausibleEmbeds = v; }

    public String getPlausibleDefaultEmbed() { return plausibleDefaultEmbed; }
    public void setPlausibleDefaultEmbed(String v) { this.plausibleDefaultEmbed = v; }

    public boolean usePlausibleEmbed() { return usePlausibleEmbed; }
    public void setUsePlausibleEmbed(boolean v) { this.usePlausibleEmbed = v; }

    @SuppressWarnings("unchecked")
    public static DeskSystemConfiguration fetch(ContentManager cm, Subject subject) {
        DeskSystemConfiguration config = new DeskSystemConfiguration();
        try {
            ContentVersionId vid = cm.resolve(EXT_ID, subject);
            if (vid != null) {
                ContentResult<Object> cr = cm.get(vid, null, Object.class, null, subject);
                if (cr.getStatus().isSuccess() && cr.getContent() != null) {
                    Object data = cr.getContent().getContentData();
                    if (data instanceof Map<?,?> map) {
                        config.setProperties((Map<String, Object>) map);
                        Object domains = map.get("plausibleDomains");
                        if (domains instanceof Map<?,?>) {
                            config.setPlausibleDomains((Map<String, String>) domains);
                        }
                        Object embeds = map.get("plausibleEmbeds");
                        if (embeds instanceof Map<?,?>) {
                            config.setPlausibleEmbeds((Map<String, String>) embeds);
                        }
                        Object defaultEmbed = map.get("plausibleDefaultEmbed");
                        if (defaultEmbed instanceof String s) {
                            config.setPlausibleDefaultEmbed(s);
                        }
                        Object useEmbed = map.get("usePlausibleEmbed");
                        if (useEmbed instanceof Boolean b) {
                            config.setUsePlausibleEmbed(b);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Return default empty config
        }
        return config;
    }
}
