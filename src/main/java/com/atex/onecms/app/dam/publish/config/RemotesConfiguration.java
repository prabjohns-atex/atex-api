package com.atex.onecms.app.dam.publish.config;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class RemotesConfiguration {
    private Map<String, RemoteConfigBean> configurations;
    private List<RemoteConfigRuleBean> publishRules;
    private Map<String, String> defaults;

    public Map<String, RemoteConfigBean> getConfigurations() {
        return configurations != null ? configurations : Collections.emptyMap();
    }

    public void setConfigurations(Map<String, RemoteConfigBean> v) { this.configurations = v; }

    public List<RemoteConfigRuleBean> getPublishRules() {
        return publishRules != null ? publishRules : Collections.emptyList();
    }

    public void setPublishRules(List<RemoteConfigRuleBean> v) { this.publishRules = v; }

    public Map<String, String> getDefaults() {
        return defaults != null ? defaults : Collections.emptyMap();
    }

    public void setDefaults(Map<String, String> v) { this.defaults = v; }

    public String getDefaultRemoteConfigId() {
        return getDefaults().get("configId");
    }

    public String getDefaultPublishConfigId() {
        String id = getDefaults().get("publishConfigId");
        return id != null ? id : "com.atex.onecms.dam.beanPublisher.Configuration";
    }

    public String getDefaultImportConfigId() {
        String id = getDefaults().get("importConfigId");
        return id != null ? id : "com.atex.onecms.dam.beanImporter.Configuration";
    }
}
